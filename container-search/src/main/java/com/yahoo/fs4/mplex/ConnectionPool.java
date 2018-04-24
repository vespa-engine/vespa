// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.mplex;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import com.yahoo.log.LogLevel;
/**
 * Pool of FS4 connections.
 *
 * @author Tony Vaagenes
 */
public class ConnectionPool {

    private final static int CLEANINGPERIOD = 1000; // Execute every second
    private final Queue<FS4Connection> connections = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger passiveConnections = new AtomicInteger(0);
    private static final Logger log = Logger.getLogger(ConnectionPool.class.getName());

    class PoolCleanerTask extends TimerTask {
        private final ConnectionPool connectionPool;
        public PoolCleanerTask(ConnectionPool connectionPool) {
            this.connectionPool = connectionPool;
        }

        public void run() {
            try {
                connectionPool.dropInvalidConnections();
            } catch (Exception e) {
                log.log(LogLevel.WARNING,
                        "Caught exception in connection pool cleaner, ignoring.",
                        e);
            }
        }
    }

    public ConnectionPool() {
    }

    public ConnectionPool(Timer timer) {
        timer.schedule(new PoolCleanerTask(this), CLEANINGPERIOD, CLEANINGPERIOD);
    }

    private void dropInvalidConnections() {
        for (Iterator<FS4Connection> i = connections.iterator(); i.hasNext();) {
            FS4Connection connection = i.next();
            if (!connection.isValid()) {
                i.remove();
            }
        }
    }

    private FS4Connection registerAsActiveIfNonZero(FS4Connection connection) {
        activeConnections.incrementAndGet();
        passiveConnections.decrementAndGet();
        return connection;
    }

    public FS4Connection getConnection() {
        return registerAsActiveIfNonZero(connections.poll());
    }

    void releaseConnection(FS4Connection connection) {
        assert(connection != null);
        activeConnections.decrementAndGet();
        if (connection.isValid()) {
            passiveConnections.incrementAndGet();
            connections.add(connection);
        }
    }

    void createdConnection() {
        activeConnections.incrementAndGet();
    }

    int activeConnections() {
        return activeConnections.get();
    }

    //unused connections in the pool
    int passiveConnections() {
        return passiveConnections.get();
    }
}
