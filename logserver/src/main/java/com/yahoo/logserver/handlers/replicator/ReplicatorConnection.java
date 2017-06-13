// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.replicator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import com.yahoo.io.Connection;
import com.yahoo.io.IOUtils;
import com.yahoo.io.Listener;
import com.yahoo.io.ReadLine;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.filter.LogFilter;
import com.yahoo.logserver.filter.LogFilterManager;
import com.yahoo.logserver.formatter.LogFormatter;
import com.yahoo.logserver.formatter.LogFormatterManager;

/**
 * Replication client connection.
 *
 * @author Bjorn Borud
 */
public class ReplicatorConnection implements Connection, LogFilter {
    private static final Logger log = Logger.getLogger(ReplicatorConnection.class.getName());

    /**
     * The maximum number of queued messages before we start dropping
     */
    private static final int maxQueueLength;
    /**
     * The maximum number of times we go over maxQueueLength before we log a warning
     */
    private static final int maxRetriesBeforeWarning = 10;
    /**
     * Count of how many times we have received a message while the queue is full
     */
    private static int queueFullCount = 0;

    static {
        String maxQueue = System.getProperty("logserver.replicator.maxqueuelength",
                                             "5000");
        maxQueueLength = Integer.parseInt(maxQueue);
    }

    private final SocketChannel socket;
    private final String remoteHost;
    private final Listener listener;
    private final Replicator replicator;
    private final LinkedList<ByteBuffer> writeBufferList = new LinkedList<ByteBuffer>();
    private ByteBuffer writeBuffer;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(4096);
    private LogFilter filter = null;
    protected LogFormatter formatter = null;
    private String filterName = "system.mute";
    private String formatterName = "system.nullformatter";
    private boolean droppingMode = false;
    private int numHandled = 0;
    private int numQueued = 0;
    private int numDropped = 0;
    private long totalBytesWritten = 0;

    /**
     * Constructs a ReplicatorConnection.  Note that initially the
     * filter of this connection is set to MuteFilter, which mutes
     * all log messages.
     */
    public ReplicatorConnection(SocketChannel socket, Listener listener, Replicator replicator) {
        this.socket = socket;
        this.listener = listener;
        this.replicator = replicator;
        this.filter = LogFilterManager.getLogFilter(filterName);
        this.formatter = LogFormatterManager.getLogFormatter(formatterName);

        // this might take some time
        remoteHost = socket.socket().getInetAddress().getHostName();

    }

    /**
     * Returns the remote hostname of this replicator connection.
     *
     * @return Returns the remote host name
     */
    public String getRemoteHost() {
        return remoteHost;
    }

    /**
     * Check if the message is wanted by this particular replicator
     * connection.  The reason we provide this method is because we
     * want to be able to determine of a message is wanted by any
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
        if (writeBuffer == null) {
            writeBuffer = buffer;
        } else {
            // if we've reached the max we bail out
            if (writeBufferList.size() > maxQueueLength) {
                queueFullCount++;
                if (! droppingMode) {
                    droppingMode = true;
                    String message = "client at " + remoteHost + " can't keep up, dropping messages";
                    if (queueFullCount > maxRetriesBeforeWarning) {
                        log.log(LogLevel.WARNING, message);
                        queueFullCount = 0;
                    } else {
                        log.log(LogLevel.DEBUG, message);
                    }
                }
                numDropped++;
                return;
            }
            writeBufferList.addLast(buffer);
            listener.modifyInterestOps(this, SelectionKey.OP_WRITE, true);
            droppingMode = false;
            numQueued++;
        }
        numHandled++;
        write();
    }


    public void read() throws IOException {
        if (! readBuffer.hasRemaining()) {
            log.warning("Log message too long. Message exceeds "
                                + readBuffer.capacity()
                                + " bytes.  Connection dropped.");
            close();
            return;
        }


        int ret = socket.read(readBuffer);
        if (ret == - 1) {
            close();
            return;
        }

        if (ret == 0) {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.fine("zero byte read occurred");
            }
        }

        readBuffer.flip();

        String s;
        while ((s = ReadLine.readLine(readBuffer)) != null) {
            onCommand(s);
        }

    }

    public synchronized void write() throws IOException {
        if (! socket.isOpen()) {
            // throw new IllegalStateException("SocketChannel not open in write()");
            close();
        }

        int bytesWritten;
        do {
            // if writeBufferList is not set we need to fetch the next buffer
            if (writeBuffer == null) {

                // if the list is empty, signal the selector we do not need
                // to do any writing for a while yet and bail
                if (writeBufferList.isEmpty()) {
                    listener.modifyInterestOpsBatch(this,
                                                    SelectionKey.OP_WRITE,
                                                    false);
                    return;
                }
                writeBuffer = writeBufferList.removeFirst();
            }


            // invariants: we have a writeBuffer

            // when the client drops off we actually need
            // to handle that here and close the connection
            // XXX: I am not sure why this works and the
            //      close method call on IOException in
            //      Listener doesn't.  this should be investigated!
            //
            try {
                bytesWritten = socket.write(writeBuffer);
            } catch (IOException e) {
                close();
                return;
            }
            totalBytesWritten += bytesWritten;

            // buffer drained so we forget it and see what happens when we
            // go around.  if indeed we go around
            if ((writeBuffer != null) && (! writeBuffer.hasRemaining())) {
                writeBuffer = null;
            }
        } while (bytesWritten > 0);
    }

    public synchronized void close() throws IOException {
        replicator.deRegisterConnection(this);
        socket.close();
        writeBuffer = null;
        writeBufferList.clear();
        log.log(LogLevel.DEBUG, "closing connection to " + remoteHost);
    }

    public int selectOps() {
        return SelectionKey.OP_READ;
    }

    public SocketChannel socketChannel() {
        return socket;
    }

    public void connect() {
    }


    // ========================================================
    // ==== command processing
    // ========================================================

    void onCommand(String s) {
        log.log(LogLevel.DEBUG, "COMMAND: '" + s + "' from " + remoteHost);
        StringTokenizer st = new StringTokenizer(s.toLowerCase());
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            if ("ping".equals(tok)) {
                if (st.hasMoreTokens()) {
                    print("# 202 pong " + st.nextToken() + "\n");
                } else {
                    print("# 202 pong\n");
                }
                return;
            }

            if ("use".equals(tok)) {
                if (st.hasMoreTokens()) {
                    onUse(st.nextToken());
                }
                return;
            }

            if ("formatter".equals(tok)) {
                if (st.hasMoreTokens()) {
                    onFormatter(st.nextToken());
                }
                return;
            }

            if ("quit".equals(tok)) {
                print("# 201 bye\n");
                try { close(); } catch (IOException e) {e.printStackTrace();}
                return;
            }

            if ("list".equals(tok)) {
                onList();
                return;
            }

            if ("listformatters".equals(tok)) {
                onListFormatters();
                return;
            }

            if ("stats".equals(tok)) {
                onStats();
                return;
            }
        }
    }

    void onFormatter(String formatterName) {
        LogFormatter newFormatter = LogFormatterManager.getLogFormatter(formatterName);
        if (newFormatter == null) {
            print("# 405 formatter not found '" + formatterName + "'\n");
            return;
        }
        formatter = newFormatter;
        this.formatterName = formatterName;
        print("# 202 using '" + formatter + "'\n");
    }

    void onUse(String filterName) {
        LogFilter newFilter = LogFilterManager.getLogFilter(filterName);
        if (newFilter == null) {
            print("# 404 filter not found '" + filterName + "'\n");
            return;
        }
        filter = newFilter;
        this.filterName = filterName;
        print("# 200 using '" + filter + "'\n");
    }


    void onList() {
        print("# 203 filter list\n");
        String filterNames[] = LogFilterManager.getFilterNames();
        for (int i = 0; i < filterNames.length; i++) {
            LogFilter f = LogFilterManager.getLogFilter(filterNames[i]);
            print("# 204 " + filterNames[i] + " - " + f.description() + "\n");
        }
        print("# 205 end filter list\n");
    }

    void onListFormatters() {
        print("# 206 formatter list\n");
        String formatterNames[] = LogFormatterManager.getFormatterNames();
        for (int i = 0; i < formatterNames.length; i++) {
            LogFormatter fmt = LogFormatterManager.getLogFormatter(formatterNames[i]);
            print("# 207 " + formatterNames[i] + " - " + fmt.description() + "\n");
        }
        print("# 208 end formatter list\n");
    }

    private void print(String s) {
        try {
            enqueue(IOUtils.utf8ByteBuffer(s));
        } catch (IOException e) {
            log.log(LogLevel.WARNING, "error printing", e);
            try {
                close();
            } catch (IOException e2) {
                // ignore
            }
        }
    }

    void onStats() {

        print(new StringBuilder(80)
                      .append("# 206 stats start (this connection)\n")
                      .append("# 207 ").append(numHandled).append(" handled\n")
                      .append("# 208 ").append(numDropped).append(" dropped\n")
                      .append("# 209 ").append(numQueued)
                      .append(" handled and queued\n")
                      .append("# 210 ").append(totalBytesWritten)
                      .append(" total bytes written\n")
                      .append("# 211 stats end\n")
                      .toString()
        );
    }


    public int getNumHandled() {
        return numHandled;
    }

    public int getNumQueued() {
        return numQueued;
    }

    public int getNumDropped() {
        return numDropped;
    }

    public long getTotalBytesWritten() {
        return totalBytesWritten;
    }

    public String getLogFilterName() {
        return filterName;
    }

    void setFilter(LogFilter filter) {
        this.filter = filter;
    }

}

