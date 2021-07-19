/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */
package com.artipie.http.misc;

import java.util.concurrent.ExecutorService;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Async pipeline for flow processor to connect upstream subscriber and downstream susbscription.
 * @param <D> Downstream type
 * @since 1.0
 */
public final class Pipeline<D> implements Subscriber<D>, Subscription {

    /**
     * Synchronization object.
     */
    private final Object lock;

    /**
     * Async executor.
     */
    private final ExecutorService exec;

    /**
     * Downstream subscriber.
     */
    private volatile Subscriber<? super D> downstream;

    /**
     * Upstream Subscription.
     */
    private volatile Subscription upstream;

    /**
     * New pipeline.
     * @param exec Async executor
     */
    public Pipeline(final ExecutorService exec) {
        this.lock = new Object();
        this.exec = exec;
    }

    /**
     * Connect downstream.
     * @param sub Downstream subscriber
     */
    public void connect(final Subscriber<? super D> sub) {
        this.exec.submit(
            () -> {
                synchronized (this.lock) {
                    if (this.downstream != null) {
                        sub.onSubscribe(DummySubscription.VALUE);
                        sub.onError(new IllegalStateException("Downstream already connected"));
                        return;
                    }
                    this.downstream = sub;
                    this.checkRequest();
                }
            }
        );
    }


    @Override
    public void onComplete() {
        this.exec.submit(() -> this.downstream.onComplete());
    }

    @Override
    public void onError(final Throwable err) {
        this.exec.submit(() -> this.downstream.onError(err));
    }

    @Override
    public void onNext(final D item) {
        this.exec.submit(() -> this.downstream.onNext(item));
    }

    @Override
    public void onSubscribe(final Subscription sub) {
        this.exec.submit(
            () -> {
                synchronized (this.lock) {
                    if (this.upstream != null) {
                        throw new IllegalStateException("Can't subscribe twice");
                    }
                    this.upstream = sub;
                    this.checkRequest();
                }
            }
        );
    }

    @Override
    public void cancel() {
        this.exec.submit(() -> this.upstream.cancel());
    }

    @Override
    public void request(final long amt) {
        this.exec.submit(() -> this.upstream.request(amt));
    }

    /**
     * Check if all required parts are connected, and request from upstream if so.
     */
    private void checkRequest() {
        if (this.downstream != null && this.upstream != null) {
            this.upstream.request(1);
        }
    }
}
