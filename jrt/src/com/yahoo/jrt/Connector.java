// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.concurrent.ThreadFactoryFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class Connector implements AutoCloseable {

    //TODO Move to vespajlib as utility as java does not have what we want.
    private static final Object globalLock = new Object();
    private static ExecutorService globalPrimaryExecutor = null;
    private static ExecutorService globalFallbackExecutor = null;
    private static long usages = 0;

    private static class ExecutorWithFallback implements Executor {
        private final Executor primary;
        private final Executor secondary;
        ExecutorWithFallback(Executor primary, Executor secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public void execute(Runnable command) {
            try {
                primary.execute(command);
            } catch (RejectedExecutionException e1) {
                secondary.execute(() -> retryForever(command));
            }
        }
        private void retryForever(Runnable command) {
            while (true) {
                try {
                    primary.execute(command);
                    return;
                } catch (RejectedExecutionException rejected) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException silenced) { }
                }
            }
        }
    }

    private static ExecutorWithFallback acquire() {
        synchronized (globalLock) {
            if (globalPrimaryExecutor == null) {
                if (globalFallbackExecutor != null) {
                    throw new IllegalStateException("fallback executor must be null !");
                }
                if (usages != 0) {
                    throw new IllegalStateException("usages " + usages + " != 0");
                }
                globalPrimaryExecutor = new ThreadPoolExecutor(1, 64, 1L, TimeUnit.SECONDS,
                        new SynchronousQueue<>(), ThreadFactoryFactory.getDaemonThreadFactory("jrt.connector.primary"));
                globalFallbackExecutor = Executors.newSingleThreadExecutor(ThreadFactoryFactory.getDaemonThreadFactory("jrt.connector.fallback"));
            }
            usages++;
            return new ExecutorWithFallback(globalPrimaryExecutor, globalFallbackExecutor);
        }
    }

    private static void join(ExecutorService executor) {
        while (true) {
            try {
                if (executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {}
        }
    }

    private static void release(ExecutorWithFallback executor) {
        synchronized (globalLock) {
            if (executor.primary != globalPrimaryExecutor) {
                throw new IllegalStateException("primary executor " + executor.primary + " != " + globalPrimaryExecutor);
            }
            if (executor.secondary != globalFallbackExecutor) {
                throw new IllegalStateException("secondary executor " + executor.secondary + " != " + globalFallbackExecutor);
            }
            usages--;
            if (usages == 0) {
                globalPrimaryExecutor.shutdown();
                globalFallbackExecutor.shutdown();
                join(globalPrimaryExecutor);
                globalPrimaryExecutor = null;
                join(globalFallbackExecutor);
                globalFallbackExecutor = null;
            }
        }
    }

    private final AtomicReference<ExecutorWithFallback> executor;

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

    public void close() {
        ExecutorWithFallback toShutdown = executor.getAndSet(null);
        if (toShutdown != null) {
            release(toShutdown);
        }
    }
}
