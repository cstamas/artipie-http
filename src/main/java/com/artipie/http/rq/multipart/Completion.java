/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */
package com.artipie.http.rq.multipart;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Multi-downstream completion handler.
 * @param <T> Type of downstream subscriber
 * @since 1.0
 */
final class Completion<T> {

    /**
     * Fake implementation for tests.
     * @since 1.0
     * @checkstyle AnonInnerLengthCheck (25 lines)
     */
    static final Completion<?> FAKE = new Completion<>(
        new Subscriber<Object>() {
            @Override
            public void onSubscribe(final Subscription sub) {
                // do nothing
            }

            @Override
            public void onNext(final Object next) {
                // do nothing
            }

            @Override
            public void onError(final Throwable err) {
                // do nothing
            }

            @Override
            public void onComplete() {
                // do nothing
            }
        }
    );

    /**
     * Downstreams counter.
     */
    private volatile int counter;

    /**
     * Upstream completed flag.
     */
    private volatile boolean completed;

    /**
     * Downstream subscriber.
     */
    private final Subscriber<? super T> downstream;

    /**
     * New completion.
     * @param downstream Subscriber reference
     */
    Completion(final Subscriber<? super T> downstream) {
        this.downstream = downstream;
    }

    /**
     * Notify downstream item completed.
     */
    @SuppressWarnings("PMD.AssignmentInOperand")
    void itemCompleted() {
        synchronized (this.downstream) {
            if (--this.counter == 0 && this.completed) {
                this.downstream.onComplete();
            }
        }
    }

    /**
     * Notify downstream item started.
     */
    void itemStarted() {
        synchronized (this.downstream) {
            assert !this.completed;
            ++this.counter;
        }
    }

    /**
     * Notify upstream completed.
     */
    void upstreamCompleted() {
        synchronized (this.downstream) {
            this.completed = true;
            if (this.counter == 0) {
                this.downstream.onComplete();
            }
        }
    }
}
