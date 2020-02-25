// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.concurrent.ThreadFactoryFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

class Connector {
    private static final Logger log = Logger.getLogger(Connector.class.getName());

    private final ExecutorService executor = new ThreadPoolExecutor(1, 64, 1L, TimeUnit.SECONDS,
                                                                    new SynchronousQueue<>(),
                                                                    ThreadFactoryFactory.getDaemonThreadFactory("jrt.connector"));

    private void connect(Connection conn) {
        conn.transportThread().addConnection(conn.connect());
    }

    public void connectLater(Connection conn) {
        long delay = 1;
        while (!executor.isShutdown()) {
            try {
                executor.execute(() -> connect(conn));
                return;
            } catch (RejectedExecutionException ignored) {
                log.warning("Failed posting connect task for " + conn + ". Trying again in " + delay + "ms.");
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException silenced) {}
                delay = Math.min(delay * 2, 100);
            }
        }
        conn.transportThread().addConnection(conn);
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
