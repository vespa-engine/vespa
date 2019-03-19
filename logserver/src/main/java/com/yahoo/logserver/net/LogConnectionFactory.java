// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.net;

import com.yahoo.io.Connection;
import com.yahoo.io.ConnectionFactory;
import com.yahoo.io.Listener;
import com.yahoo.logserver.LogDispatcher;

import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Bjorn Borud
 */
public class LogConnectionFactory implements ConnectionFactory {
    private static final Logger log = Logger.getLogger(LogConnectionFactory.class.getName());

    private final LogDispatcher dispatcher;

    public LogConnectionFactory(LogDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public Connection newConnection(SocketChannel socket, Listener listener) {
        if (log.isLoggable(Level.FINE)) {
            log.fine("New connection: " + socket);
        }
        return new LogConnection(socket,
                                 listener,
                                 dispatcher);
    }
}
