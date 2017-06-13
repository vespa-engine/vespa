// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.nio.channels.SocketChannel;
import java.io.IOException;


/**
 * Connection interface is the abstraction for an operating
 * asynchronous NIO connection.  One is created for each
 * "accept" on the channel.
 *
 * @author <a href="mailto:travisb@yahoo-inc.com">Bob Travis</a>
 * @author <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 *
 */
public interface Connection {

    /**
     * called when the channel can accept a write, and is
     * enabled for writing
     */
    public void write() throws IOException;

    /**
     * Called when the channel can accept a read, and is
     * enabled for reading
     */
    public void read() throws IOException;

    /**
     * Called when the channel should be closed.
     */
    public void close() throws IOException;

    /**
     * Called when a socket has completed connecting to its
     * destination.  (Asynchronous connect)
     */
    public void connect() throws IOException;

    /**
     * called to get the correct initial SelectionKey operation
     * flags for the next Select cycle, for this channel
     */
    public int selectOps();

    /**
     * Called to get the SocketChannel for this Connection.
     *
     * @return Returns the SocketChannel representing this connection
     */
    public SocketChannel socketChannel();
}

