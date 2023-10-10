// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.executor;

import java.util.logging.Level;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * @author hakonhall
 */
public class RunletExecutorImpl implements RunletExecutor {
    private static Logger logger = Logger.getLogger(RunletExecutorImpl.class.getName());

    private final AtomicInteger executionId = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, CancellableImpl> cancellables = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor executor;

    public RunletExecutorImpl(int threadPoolSize) {
        executor = new ScheduledThreadPoolExecutor(threadPoolSize);
    }

    public Cancellable scheduleWithFixedDelay(Runlet runlet, Duration delay) {
        Duration initialDelay = Duration.ofMillis((long) ThreadLocalRandom.current().nextInt((int) delay.toMillis()));
        CancellableImpl cancellable = new CancellableImpl(runlet);
        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(cancellable, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
        cancellable.setPeriodicExecutionCancellationCallback(() -> future.cancel(false));
        Integer id = executionId.incrementAndGet();
        cancellables.put(id, cancellable);
        return () -> cancelRunlet(id);
    }

    private void cancelRunlet(Integer id) {
        CancellableImpl cancellable = cancellables.remove(id);
        if (cancellable != null) {
            cancellable.cancel();
        }
    }

    @Override
    public void close() {
        // At this point no-one should be scheduling new runlets, so this ought to clear the map.
        cancellables.keySet().forEach(this::cancelRunlet);

        if (cancellables.size() > 0) {
            throw new IllegalStateException("Runlets scheduled while closing the executor");
        }

        // The cancellables will cancel themselves from the executor only after up-to delay time,
        // so wait until all have drained.
        while (executor.getQueue().size() > 0) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) { }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Timed out waiting for termination of executor", e);
        }
    }
}
