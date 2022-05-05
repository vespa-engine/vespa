// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.impl;


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
public abstract class ProcessingFuture<V> extends CompletableFuture<V> {

    @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
    @Override public boolean isCancelled() { return false; }

    @Override public abstract V get() throws InterruptedException, ExecutionException;
    @Override public abstract V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

    public void addListener(Runnable listener, Executor executor) {
        whenCompleteAsync((__, ___) -> listener.run(), executor);
    }

}
