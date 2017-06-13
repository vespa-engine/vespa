// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.lasterrorsholder;

import com.yahoo.io.Connection;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.filter.LogFilter;
import com.yahoo.logserver.filter.LogFilterManager;
import com.yahoo.logserver.formatter.LogFormatter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

/**
 * LastErrorsHandler client connection.
 *
 * @author hmusum
 */
public class LastErrorsHolderConnection implements Connection, LogFilter {
    private static final Logger log = Logger.getLogger(LastErrorsHolderConnection.class.getName());

    private final SocketChannel socket;
    private ByteBuffer writeBuffer;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(4096);
    private LogFilter filter = null;
    protected LogFormatter formatter = null;
    private static final String filterName = "system.mute";

    /**
     * Constructs a LastErrorsHolderConnection.  Note that initially the
     * filter of this connection is set to MuteFilter, which mutes
     * all log messages.
     */
    public LastErrorsHolderConnection(SocketChannel socket) {
        this.socket = socket;
        this.filter = LogFilterManager.getLogFilter(filterName);
    }

    /**
     * Check if the message is wanted by this particular replicator
     * connection.  The reason we provide this method is because we
     * want to be able to determine if a message is wanted by any
     * client before committing resources to creating a ByteBuffer to
     * serialize it into.
     *
     * @param msg The log message offered
     */
    public boolean isLoggable(LogMessage msg) {
        if (filter == null) {
            return true;
        }
        return filter.isLoggable(msg);
    }

    /**
     * Return the description of the currently active filter.
     */
    public String description() {
        if (filter == null) {
            return "No filter defined";
        }
        return filter.description();
    }


    /**
     * Enqueues a ByteBuffer containing the message destined
     * for the client.
     *
     * @param buffer the ByteBuffer into which the log message is
     *               serialized.
     */
    public synchronized void enqueue(ByteBuffer buffer) throws IOException {
        writeBuffer = buffer;
        write();
    }

    public void read() throws IOException {
        if (!readBuffer.hasRemaining()) {
            log.warning("Log message too long. Message exceeds "
                    + readBuffer.capacity()
                    + " bytes.  Connection dropped.");
            close();
            return;
        }


        int ret = socket.read(readBuffer);
        if (ret == -1) {
            close();
            return;
        }

        if (ret == 0) {
            if (log.isLoggable(LogLevel.INFO)) {
                log.log(LogLevel.INFO, "zero byte read occurred");
            }
        }

        readBuffer.flip();
    }

    public synchronized void write() throws IOException {
        if (!socket.isOpen()) {
            close();
        }
        do {
            try {
                socket.write(writeBuffer);
            } catch (IOException e) {
                log.log(LogLevel.WARNING, "Error writing", e);
                close();
                return;
            }
        } while (writeBuffer.hasRemaining());
    }

    public synchronized void close() throws IOException {
        socket.close();
        writeBuffer = null;
    }

    public int selectOps() {
        return SelectionKey.OP_READ;
    }

    public SocketChannel socketChannel() {
        return socket;
    }

    public void connect() {
    }

    void setFilter(LogFilter filter) {
        this.filter = filter;
    }
}

