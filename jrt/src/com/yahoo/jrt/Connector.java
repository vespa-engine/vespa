// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.concurrent.ThreadFactoryFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

class Connector {

    private final ExecutorService executor = Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory("jrt.connector"));

    private void connect(Connection conn) {
        conn.transportThread().addConnection(conn.connect());
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
        return this;
    }

    public void join() {
        while (true) {
            try {
                if (executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {}
        }
    }
}
