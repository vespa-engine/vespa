// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.replicator;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import com.yahoo.io.Connection;
import com.yahoo.io.ConnectionFactory;
import com.yahoo.io.Listener;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.handlers.AbstractLogHandler;

/**
 * The Replicator plugin is used for replicating log messages sent
 * to the logserver.
 * <p>
 * <p>
 * Per default the replicator will start dropping messages enqueued
 * to a client if the outbound message queue reaches 5000 messages.
 * This limit can be configured by setting the system property
 * <code>logserver.replicator.maxqueuelength</code> to the desired
 * value.
 *
 * @author Bjorn Borud
 */
public class Replicator extends AbstractLogHandler implements ConnectionFactory {
    private static final Logger log = Logger.getLogger(Replicator.class.getName());

    private int port;
    private Listener listener;
    private final Set<ReplicatorConnection> connections = new HashSet<ReplicatorConnection>();
    private final FormattedBufferCache bufferCache = new FormattedBufferCache();

    /**
     * @param port The port to which the replicator listens.
     */
    public Replicator(int port) throws IOException {
        this.port = port;
        listen(port);
    }

    public Replicator() {
    }

    public void listen(int port) throws IOException {
        if (listener != null) {
            throw new IllegalStateException("already listening to port " + this.port);
        }
        listener = new Listener("replicator");
        listener.listen(this, port);
        listener.start();
        log.log(LogLevel.CONFIG, "port=" + port);
    }

    public synchronized boolean doHandle(LogMessage msg) {
        boolean logged = false;
        bufferCache.reset();
        for (ReplicatorConnection c : connections) {
            try {
                if (c.isLoggable(msg)) {
                    c.enqueue(bufferCache.getFormatted(msg, c.formatter));
                    logged = true;
                }
            } catch (IOException e) {
                log.log(LogLevel.DEBUG, "Writing failed", e);
            }
        }
        return logged;
    }

    public void close() {
        // kill the listener thread, then wait for it to
        // shut down.
        try {
            listener.interrupt();
            listener.join();
            log.log(LogLevel.DEBUG, "Replicator listener stopped");
        } catch (InterruptedException e) {
            log.log(LogLevel.WARNING,
                    "Replicator listener was interrupted",
                    e);
        }
    }

    /**
     * Currently a NOP, but we might want to have some best-effort
     * mechanism for trying to flush all connections within some
     * time-frame.
     */
    public void flush() {}

    /**
     * Factory method for wrapping new connections in the proper
     * (Replicator)Connection objects.
     *
     * @param socket   The new SocketChannel
     * @param listener The Listener instance we want to use
     */
    public synchronized Connection newConnection(SocketChannel socket,
                                                 Listener listener) {
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.fine("New replicator connection: " + socket);
        }
        ReplicatorConnection n =
                new ReplicatorConnection(socket, listener, this);
        connections.add(n);
        return n;
    }

    /**
     * Removes a ReplicatorConnection from the set of active
     * connections.
     */
    protected synchronized void deRegisterConnection(ReplicatorConnection conn) {
        connections.remove(conn);
    }

    public String toString() {
        return Replicator.class.getName();
    }

}
