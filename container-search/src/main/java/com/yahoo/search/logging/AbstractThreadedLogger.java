// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.logging;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

abstract class AbstractThreadedLogger implements Logger {

    private final static java.util.logging.Logger log = java.util.logging.Logger.getLogger(AbstractThreadedLogger.class.getName());

    final static int DEFAULT_MAX_THREADS = 1;
    final static int DEFAULT_QUEUE_SIZE = 1000;

    protected final WorkerThreadExecutor executor;

    AbstractThreadedLogger() {
        this(DEFAULT_MAX_THREADS, DEFAULT_QUEUE_SIZE);
    }

    AbstractThreadedLogger(int threads, int queueSize) {
        executor = new WorkerThreadExecutor(threads, queueSize);
    }

    AbstractThreadedLogger(int threads, int queueSize, ThreadFactory factory) {
        executor = new WorkerThreadExecutor(threads, queueSize, factory);
    }

    @Override
    public boolean send(LoggerEntry entry) {
        return enqueue(entry);
    }

    protected boolean enqueue(LoggerEntry entry) {
        // Todo: metric things
        try {
            executor.execute(() -> dequeue(entry));
        } catch (RejectedExecutionException e) {
            return false;
        }
        return true;
    }

    protected void dequeue(LoggerEntry entry) {
        transport(entry);  // This happens in worker thread
    }

    /**
     * Actually transports the entry to it's destination
     */
    public abstract boolean transport(LoggerEntry entry);


    private static class WorkerThread extends Thread {

        public WorkerThread(Runnable r) {
            super(r);
        }

        @Override
        public void run() {
            try {
                super.run();
            } catch (Exception e) {
                log.log(Level.SEVERE, String.format("Error while sending logger entry: %s", e), e);
            }
        }

    }

    protected static class WorkerThreadExecutor implements Executor {

        protected final ThreadPoolExecutor executor;

        WorkerThreadExecutor(int threads, int queueSize) {
            this(threads, queueSize, WorkerThread::new);
        }

        WorkerThreadExecutor(int threads, int queueSize, ThreadFactory threadFactory) {
            executor = new ThreadPoolExecutor(
                    threads, threads,
                    0L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueSize),
                    threadFactory);
        }

        public void close() {
            try {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                //
            } finally {
                executor.shutdownNow();
            }
        }

        @Override
        public void execute(Runnable r) {
            executor.execute(r);
        }

    }

}
