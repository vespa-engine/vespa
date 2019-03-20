// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.net;

import com.yahoo.io.Connection;
import com.yahoo.io.ReadLine;
import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.LogDispatcher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO
 * <UL>
 *  <LI> send invalid log messages to somewhere so they can be
 *       analyzed and errors can be corrected.
 * </UL>
 *
 * @author Bjorn Borud
 */

public class LogConnection implements Connection {
    private static final Logger log = Logger.getLogger(LogConnection.class.getName());

    public static final int READBUFFER_SIZE = (32 * 1024);

    // the set of active connections
    private static final Set<LogConnection> activeConnections = new HashSet<>();

    private final SocketChannel socket;
    private final LogDispatcher dispatcher;

    private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(READBUFFER_SIZE);

    // counters
    private long totalBytesRead = 0;

    public LogConnection(SocketChannel socket, LogDispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;

        addToActiveSet(this);

    }

    /**
     * Return a shallow copy of the set of active connections.
     *
     */
    public static Set<LogConnection> getActiveConnections () {
        synchronized(activeConnections) {
        	return new HashSet<>(activeConnections);
        }
    }

    /**
     * @return Return total number of bytes read from connection
     */
    public long getTotalBytesRead () {
        return totalBytesRead;
    }

    /**
     * Internal method for adding connection to the set
     * of active connections.
     *
     * @param connection The connection to be added
     */
    private static void addToActiveSet (LogConnection connection) {
        synchronized(activeConnections) {
            activeConnections.add(connection);
        }
    }

    /**
     * Internal method to remove connection from the set of
     * active connections.
     *
     * @param connection The connection to remove
     * @throws IllegalStateException if the connection does not
     *                               exist in the set
     *
     */
    private static void removeFromActiveSet (LogConnection connection) {
        synchronized(activeConnections) {
            activeConnections.remove(connection);
        }
    }

    public void connect () throws IOException {
        throw new RuntimeException("connect() is not supposed to be called");
    }

    public void write () {
        throw new UnsupportedOperationException();
    }

    public void read() throws IOException {
        if (! readBuffer.hasRemaining()) {

            try {
                readBuffer.putChar(readBuffer.capacity() - 2, '\n');
                readBuffer.flip();
                String s = ReadLine.readLine(readBuffer);
                if (s == null) {
                    return;
                }
                log.log(LogLevel.DEBUG, "Log message too long. Message from "
                        + socket.socket().getInetAddress() +  " exceeds "
                        + readBuffer.capacity() + ". The message was: " + s);

                LogMessage msg = LogMessage.parseNativeFormat(s);
                dispatcher.handle(msg);
            }
            catch (InvalidLogFormatException e) {
                log.log(LogLevel.DEBUG, "Invalid log message", e);
            }
            finally {
                readBuffer.clear();
            }
            return;
        }

        int ret = socket.read(readBuffer);
        if (ret == -1) {
            close();
            return;
        }

        if (ret == 0) {
            if (log.isLoggable(Level.FINE)) {
                log.log(LogLevel.DEBUG, "zero byte read occurred");
             }
        }

        // update global counter
        totalBytesRead += ret;

        readBuffer.flip();

        String s;
        while ((s = ReadLine.readLine(readBuffer)) != null) {
            try {
                LogMessage msg = LogMessage.parseNativeFormat(s);
                dispatcher.handle(msg);
            }
            catch (InvalidLogFormatException e) {
                log.log(LogLevel.DEBUG, "Invalid log message", e);
            }
        }
    }

    public void close() throws IOException {
        if (log.isLoggable(Level.FINE)) {
            log.log(LogLevel.INFO, this + ": closing");
        }
        socket.close();
        removeFromActiveSet(this);
    }

    public int selectOps() {
        return SelectionKey.OP_READ;
    }

    public SocketChannel socketChannel() {
        return socket;
    }

}
