// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.executor;

import java.util.logging.Level;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Provides the {@link Cancellable} returned by {@link RunletExecutorImpl#scheduleWithFixedDelay(Runlet, Duration)},
 * and ensuring the correct semantic execution of the {@link Runlet}.
 *
 * @author hakonhall
 */
class CancellableImpl implements Cancellable, Runnable {
    private static final Logger logger = Logger.getLogger(CancellableImpl.class.getName());

    private final Object monitor = new Object();
    private Runlet runlet;
    private Optional<Runnable> periodicExecutionCancellation = Optional.empty();
    private boolean running = false;
    private boolean cancelled = false;

    public CancellableImpl(Runlet runlet) {
        this.runlet = runlet;
    }

    /**
     * Provide a way for {@code this} to cancel the periodic execution of {@link #run()}.
     *
     * <p>Must be called happens-before {@link #cancel()}.
     */
    void setPeriodicExecutionCancellationCallback(Runnable periodicExecutionCancellation) {
        synchronized (monitor) {
            if (cancelled) {
                throw new IllegalStateException("Cancellation callback set after cancel()");
            }

            this.periodicExecutionCancellation = Optional.of(periodicExecutionCancellation);
        }
    }

    /**
     * Cancel the execution of the {@link Runlet}.
     *
     * <ul>
     *     <li>Either the runlet will not execute any more {@link Runlet#run()}s, and {@link Runlet#close()}
     *     and then {@code periodicExecutionCancellation} will be called synchronously, or
     *     <li>{@link #run()} is executing concurrently by another thread {@code T}. The last call to
     *     {@link Runlet#run()} will be called by {@code T} shortly, is in progress, or has completed.
     *     Then {@code T} will call {@link Runlet#close()} followed by {@code periodicExecutionCancellation},
     *     before the return of {@link #run()}.
     * </ul>
     *
     * <p>{@link #setPeriodicExecutionCancellationCallback(Runnable)} must be called happens-before this method.
     */
    @Override
    public void cancel() {
        synchronized (monitor) {
            if (!periodicExecutionCancellation.isPresent()) {
                throw new IllegalStateException("setPeriodicExecutionCancellationCallback has not been called before cancel");
            }

            cancelled = true;
            if (running) return;
        }

        runlet.close();
        periodicExecutionCancellation.get().run();
    }

    /**
     * Must be called periodically in happens-before order, but may be called concurrently with
     * {@link #setPeriodicExecutionCancellationCallback(Runnable)} and {@link #cancel()}.
     */
    @Override
    public void run() {
        try {
            synchronized (monitor) {
                if (cancelled) return;
                running = true;
            }

            runlet.run();

            synchronized (monitor) {
                running = false;
                if (!cancelled) return;

                if (!periodicExecutionCancellation.isPresent()) {
                    // This should be impossible given the implementation of cancel()
                    throw new IllegalStateException("Cancelled before cancellation callback was set");
                }
            }

            runlet.close();
            periodicExecutionCancellation.get().run();
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed run of periodic execution", e);
        }
    }
}
