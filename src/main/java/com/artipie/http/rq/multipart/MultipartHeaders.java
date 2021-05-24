/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */
package com.artipie.http.rq.multipart;

import com.artipie.http.Headers;
import com.artipie.http.headers.Header;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Multipart headers builder.
 * <p>
 * Multipart headers are created from byte-buffer chunks.
 * The chunk-receiver pushes buffers to this builder.
 * When complete, it returns this headers wrapper and
 * it lazy parses and construt headers collection.
 * After reading headers iterable, the temporary buffer
 * becomes invalid.
 * @since 1.0
 */
final class MultipartHeaders implements Headers {

    /**
     * Sync lock.
     */
    private final Object lock;

    /**
     * Temporary data buffer for accumulation.
     */
    private volatile ByteBuffer data;

    /**
     * Headers instance cache constructed from buffer.
     */
    private volatile Headers cache;

    /**
     * New headers builder with initial capacity.
     * @param cap Initial capacity
     */
    MultipartHeaders(final int cap) {
        this.lock = new Object();
        this.data = ByteBuffer.allocate(cap).flip();
    }

    @Override
    @SuppressWarnings("PMD.NullAssignment")
    public Iterator<Map.Entry<String, String>> iterator() {
        if (this.cache == null) {
            synchronized (this.lock) {
                if (this.cache == null) {
                    this.data.rewind();
                    final byte[] arr = new byte[this.data.remaining()];
                    this.data.get(arr);
                    final String hstr = new String(arr, StandardCharsets.US_ASCII);
                    this.cache = new Headers.From(
                        Arrays.stream(hstr.split("\r\n")).map(
                            line -> {
                                final String[] parts = line.split(":");
                                return new Header(
                                    parts[0].trim().toLowerCase(Locale.US),
                                    parts[1].trim().toLowerCase(Locale.US)
                                );
                            }
                        ).collect(Collectors.toList())
                    );
                }
                this.data = null;
            }
        }
        return this.cache.iterator();
    }

    /**
     * Push new chunk to builder.
     * @param chunk Part of headers bytes
     */
    void push(final ByteBuffer chunk) {
        if (this.data == null) {
            throw new IllegalStateException("Headers were constructured");
        }
        synchronized (this.lock) {
            if (this.data.capacity() - this.data.limit() >= chunk.remaining()) {
                this.data.limit(this.data.limit() + chunk.remaining());
                this.data.put(chunk);
            } else {
                final ByteBuffer resized =
                    ByteBuffer.allocate(this.data.capacity() + chunk.capacity());
                final int pos = this.data.position();
                final int lim = this.data.limit();
                this.data.flip();
                resized.put(this.data);
                resized.limit(lim + chunk.remaining());
                resized.position(pos);
                resized.put(chunk);
                this.data = resized;
            }
        }
    }
}

