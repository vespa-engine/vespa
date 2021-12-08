// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.impl;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link CompletableFuture} where {@link #get()}/{@link #get(long, TimeUnit)} may have side-effects (e.g trigger the underlying computation).
 *
 * @author bjorncs
 */
// TODO Vespa 8 remove ListenableFuture implementation
public abstract class ProcessingFuture<V> extends CompletableFuture<V> implements ListenableFuture<V> {

    @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
    @Override public boolean isCancelled() { return false; }

    @Override public abstract V get() throws InterruptedException, ExecutionException;
    @Override public abstract V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

    @Override
    public void addListener(Runnable listener, Executor executor) {
        whenCompleteAsync((__, ___) -> listener.run(), executor);
    }

}
