// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.net;

import com.yahoo.logserver.LogDispatcher;
import com.yahoo.io.Connection;
import com.yahoo.io.Listener;
import com.yahoo.io.ConnectionFactory;

import com.yahoo.logserver.net.control.Levels;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.channels.SocketChannel;

/**
 * @author Bjorn Borud
 */
public class LogConnectionFactory implements ConnectionFactory {
    private static final Logger log = Logger.getLogger(LogConnectionFactory.class.getName());

    private final LogDispatcher dispatcher;
    private final Levels defaultLogLevels;

    public LogConnectionFactory(LogDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        defaultLogLevels = Levels.parse(System.getProperty("logserver.default.loglevels", ""));
    }

    public Connection newConnection(SocketChannel socket, Listener listener) {
        if (log.isLoggable(Level.FINE)) {
            log.fine("New connection: " + socket);
        }
        return new LogConnection(socket,
                                 listener,
                                 dispatcher,
                                 (Levels) defaultLogLevels.clone());
    }
}
