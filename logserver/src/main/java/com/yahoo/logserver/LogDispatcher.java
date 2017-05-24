// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.yahoo.io.SelectLoopHook;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.handlers.LogHandler;


/**
 * This is the central point from which LogMessage objects are
 * propagated throughout the logserver architecture.
 *
 * @author Bjorn Borud
 */
public class LogDispatcher implements LogHandler, SelectLoopHook {
    private static final Logger log = Logger.getLogger(LogDispatcher.class.getName());

    private final List<LogHandler> handlers = new ArrayList<>();
    private int messageCount = 0;
    private boolean hasBeenShutDown = false;
    private boolean batchedMode = false;
    private final int batchSize = 5000;
    private List<LogMessage> currentBatchList;
    private int roundCount = 0;
    @SuppressWarnings("unused")
    private int lastRoundCount = 0;

    public LogDispatcher() {
    }

    /**
     * Dispatches a message to all the LogHandler instances we've
     * got registered.  The main entry point for LogMessage instances
     * into the log server.
     *
     * @param msg The LogMessage instance we wish to dispatch to the
     *            plugins
     */
    public synchronized void handle(LogMessage msg) {
        if (msg == null) {
            throw new NullPointerException("LogMessage was null");
        }

        if (batchedMode) {
            addToBatch(msg);
        } else {
            for (LogHandler h : handlers) {
                h.handle(msg);
            }
        }
        messageCount++;
    }

    private void addToBatch(LogMessage msg) {
        if (currentBatchList == null) {
            currentBatchList = new ArrayList<LogMessage>(batchSize);
            currentBatchList.add(msg);
            return;
        }

        currentBatchList.add(msg);

        if (currentBatchList.size() == batchSize) {
            flushBatch();
        }
    }

    private void flushBatch() {
        List<LogMessage> todo;
        synchronized(this) {
            todo = currentBatchList;
            currentBatchList = null;
        }
        if (todo == null) return;
        for (LogHandler ht : handlers) {
            ht.handle(todo);
        }
    }

    public void handle(List<LogMessage> messages) {
        throw new IllegalStateException("method not supported");
    }

    /**
     * Set the batched mode.  Note that this should only be set
     * at initialization time because it radically changes the
     * behavior of the dispatcher.  When in batched mode, the
     * dispatcher will not enqueue single LogMessage instances
     * but lists of same.
     */
    public void setBatchedMode(boolean batchedMode) {
        this.batchedMode = batchedMode;
    }

    public synchronized void flush() {
        if (batchedMode) {
            flushBatch();
        }

        for (LogHandler h : handlers) {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Flushing " + h.toString());
            }
            h.flush();
        }
    }

    public synchronized void close() {
        if (hasBeenShutDown) {
            throw new IllegalStateException("Shutdown already in progress");
        }
        hasBeenShutDown = true;

        for (LogHandler ht : handlers) {
            if (ht instanceof Thread) {
                log.fine("Stopping " + ht);
                ((Thread) ht).interrupt();
            }
        }
        handlers.clear();

        log.log(LogLevel.DEBUG, "Logdispatcher shut down.  Handled " + messageCount + " messages");
    }

    /**
     * Register handler thread with the dispatcher.  If the handler
     * thread has already been registered, we log a warning and
     * just do nothing.
     * <p>
     * If the thread is not alive it will be start()'ed.
     */
    public synchronized void registerLogHandler(LogHandler ht) {
        if (hasBeenShutDown) {
            throw new IllegalStateException("Tried to register LogHandler on" +
                                                    " LogDispatcher which was shut down");
        }

        if (handlers.contains(ht)) {
            log.warning("LogHandler was already registered: " + ht);
            return;
        }
        handlers.add(ht);

        if ((ht instanceof Thread) && (! ((Thread) ht).isAlive())) {
            ((Thread) ht).start();
        }

        log.fine("Added (and possibly started) LogHandler " + ht);
    }

    /**
     * Make defensive copy and return array of LogHandlers.
     */
    public LogHandler[] getLogHandlers() {
        LogHandler[] h = new LogHandler[handlers.size()];
        return handlers.toArray(h);
    }

    /**
     * Return message counter.
     *
     * @return Returns the number of messages that we have seen.
     */
    public synchronized int getMessageCount() {
        return messageCount;
    }

    /**
     * Hook which is called when the select loop has finished.
     */
    public void selectLoopHook(boolean before) {
        if (batchedMode) {
            flushBatch();
        }

        lastRoundCount = messageCount - roundCount;
        roundCount = messageCount;
    }
}
