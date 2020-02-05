// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.concurrent.ThreadFactoryFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

class Connector {

    private final Transport   parent;
    private final ExecutorService executor = Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory("jrt.connector"));
    private boolean     done = false;

    private void connect(Connection conn) {
        try {
            conn.transportThread().addConnection(conn.connect());
        } catch (Throwable problem) {
            parent.handleFailure(problem, Connector.this);
        }
    }

    public Connector(Transport parent) {
        this.parent = parent;
    }

    public void connectLater(Connection conn) {
        try {
            executor.execute(() -> connect(conn));
        } catch (RejectedExecutionException e) {
            conn.transportThread().addConnection(conn);
        }

    }

    public Connector shutdown() {
        executor.shutdown();
        join();
        synchronized (this) {
            done = true;
            notifyAll();
        }
        return this;
    }

    public synchronized void waitDone() {
        while (!done) {
            try { wait(); } catch (InterruptedException x) {}
        }
    }

    public synchronized Connector exit() {
        notifyAll();
        return this;
    }

    public void join() {
        while (true) {
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
                return;
            } catch (InterruptedException e) {}
        }
    }
}
