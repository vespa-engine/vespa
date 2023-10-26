// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.executor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author hakonhall
 */
public class TestExecutor implements RunletExecutor {
    private List<Thread> threads = new ArrayList<>();

    private Runlet runlet;
    private CancellableImpl cancellable;

    private final Object monitor = new Object();
    private boolean afterRun = false;
    private boolean waitAfterRun = false;
    private int runsCompleted = 0;

    private final Runnable cancelExecution = () -> executionRunning = false;
    private volatile boolean executionRunning = true;

    @Override
    public Cancellable scheduleWithFixedDelay(Runlet runlet, Duration delay) {
        if (this.runlet != null) {
            throw new IllegalStateException("TestExecutor only supports execution of one runlet");
        }

        this.runlet = runlet;
        this.cancellable = new CancellableImpl(runlet);
        this.cancellable.setPeriodicExecutionCancellationCallback(cancelExecution);
        return this::cancel;
    }

    private void cancel() {
        cancellable.cancel();
    }

    boolean isExecutionRunning() {
        return executionRunning;
    }

    void runAsync() {
        Thread thread = new Thread(this::threadMain);
        thread.start();
        threads.add(thread);
    }

    void runToCompletion(int run) {
        runAsync();
        waitUntilRunCompleted(run);
    }

    private void threadMain() {
        cancellable.run();

        synchronized (monitor) {
            ++runsCompleted;
            afterRun = true;
            monitor.notifyAll();

            while (waitAfterRun) {
                monitor.notifyAll();
            }
            afterRun = false;
        }
    }

    void setWaitAfterRun(boolean waitAfterRun) {
        synchronized (monitor) {
            this.waitAfterRun = waitAfterRun;
        }
    }

    void waitUntilAfterRun() {
        synchronized (monitor) {
            while (!afterRun) {
                uncheckedWait();
            }
        }
    }

    void waitUntilRunCompleted(int run) {
        synchronized (monitor) {
            while (runsCompleted < run) {
                uncheckedWait();
            }
        }
    }

    void uncheckedWait() {
        try {
            monitor.wait();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        threads.forEach(thread -> { try { thread.join(); } catch (InterruptedException ignored) {} });
    }
}
