// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.executor;

/**
 * @author hakonhall
 */
public class TestRunlet implements Runlet {
    private final Object monitor = new Object();
    private boolean running = false;
    private boolean shouldWaitInRun = false;
    private boolean closed = false;
    private int runsStarted = 0;
    private int runsCompleted = 0;

    int getRunsStarted() {
        synchronized (monitor) {
            return runsStarted;
        }
    }

    int getRunsCompleted() {
        return runsCompleted;
    }

    boolean isClosed() {
        synchronized (monitor) {
            return closed;
        }
    }

    void shouldWaitInRun(boolean value) {
        synchronized (monitor) {
            shouldWaitInRun = value;
            monitor.notifyAll();
        }
    }

    void waitUntilInRun() {
        synchronized (monitor) {
            while (!running) {
                uncheckedWait();
            }
        }
    }

    void waitUntilCompleted(int runsCompleted) {
        synchronized (monitor) {
            while (this.runsCompleted < runsCompleted) {
                uncheckedWait();
            }
        }
    }

    void waitUntilClosed() {
        synchronized (monitor) {
            while (!closed) {
                uncheckedWait();
            }
        }
    }

    @Override
    public void run() {
        synchronized (monitor) {
            if (closed) {
                throw new IllegalStateException("run after close");
            }

            ++runsStarted;
            running = true;
            monitor.notifyAll();

            while (shouldWaitInRun) {
                uncheckedWait();
            }

            ++runsCompleted;
            running = false;
            monitor.notifyAll();
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            closed = true;
            monitor.notifyAll();
        }
    }

    private void uncheckedWait() {
        try {
            monitor.wait();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
