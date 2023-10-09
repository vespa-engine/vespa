// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.concurrent.CachedThreadPoolWithFallback;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class Connector implements AutoCloseable {

    private static final Object globalLock = new Object();
    private static CachedThreadPoolWithFallback globalExecutor = null;
    private static long usages = 0;

    private static CachedThreadPoolWithFallback acquire() {
        synchronized (globalLock) {
            if (globalExecutor == null) {
                globalExecutor = new CachedThreadPoolWithFallback("jrt.connector", 1, 64, 1L, TimeUnit.SECONDS);
            }
            usages++;
            return globalExecutor;
        }
    }

    private static void release(CachedThreadPoolWithFallback executor) {
        synchronized (globalLock) {
            assert executor == globalExecutor;
            usages--;
            if (usages == 0) {
                globalExecutor.close();
                globalExecutor = null;
            }
        }
    }

    private final AtomicReference<CachedThreadPoolWithFallback> executor;

    Connector() {
        executor = new AtomicReference<>(acquire());
    }

    private void connect(Connection conn) {
        conn.transportThread().addConnection(conn.connect());
    }

    public void connectLater(Connection conn) {
        Executor executor = this.executor.get();
        if (executor != null) {
            try {
                executor.execute(() -> connect(conn));
                return;
            } catch (RejectedExecutionException ignored) {
            }
        }
        conn.transportThread().addConnection(conn);
    }

    @Override
    public void close() {
        CachedThreadPoolWithFallback toShutdown = executor.getAndSet(null);
        if (toShutdown != null) {
            release(toShutdown);
        }
    }
}
