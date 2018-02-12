// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final List<LogHandler> handlers = new CopyOnWriteArrayList<>();
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final AtomicBoolean batchedMode = new AtomicBoolean(false);
    private final int batchSize = 5000;
    private final AtomicBoolean hasBeenShutDown = new AtomicBoolean(false);
    private List<LogMessage> currentBatchList = null;

    public LogDispatcher() { }

    /**
     * Dispatches a message to all the LogHandler instances we've
     * got registered.  The main entry point for LogMessage instances
     * into the log server.
     *
     * @param msg The LogMessage instance we wish to dispatch to the
     *            plugins
     */
    public void handle(LogMessage msg) {
        if (msg == null) {
            throw new NullPointerException("LogMessage was null");
        }

        if (batchedMode.get()) {
            addToBatch(msg);
        } else {
            send(msg);
        }
        messageCount.incrementAndGet();
    }

    private void addToBatch(LogMessage msg) {
        List<LogMessage> toSend = null;
        synchronized (this) {
            if (currentBatchList == null) {
                currentBatchList = new ArrayList<LogMessage>(batchSize);
                currentBatchList.add(msg);
                return;
            }

            currentBatchList.add(msg);

            if (currentBatchList.size() == batchSize) {
                toSend = stealBatch();
            }
        }
        flushBatch(toSend);
    }

    private void send(List<LogMessage> messages) {
        for (LogHandler ht : handlers) {
            ht.handle(messages);
        }
    }
    private void send(LogMessage message) {
        for (LogHandler ht : handlers) {
            ht.handle(message);
        }
    }

    private void flushBatch(List<LogMessage> todo) {
        if (todo == null) { return; }
        send(todo);
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
        this.batchedMode.set(batchedMode);
    }

    private List<LogMessage> stealBatch() {
        List<LogMessage> toSend = null;
        synchronized (this) {
            toSend = currentBatchList;
            currentBatchList = null;
        }
        return toSend;
    }
    public void flush() {
        if (batchedMode.get()) {
            flushBatch(stealBatch());
        }

        for (LogHandler h : handlers) {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Flushing " + h.toString());
            }
            h.flush();
        }
    }

    public void close() {
        if (hasBeenShutDown.getAndSet(true)) {
            throw new IllegalStateException("Shutdown already in progress");
        }

        for (LogHandler ht : handlers) {
            if (ht instanceof Thread) {
                log.fine("Stopping " + ht);
                // Todo: Very bad, never do....
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
    public void registerLogHandler(LogHandler ht) {
        if (hasBeenShutDown.get()) {
            throw new IllegalStateException("Tried to register LogHandler on LogDispatcher which was shut down");
        }

        synchronized (this) {
            if (handlers.contains(ht)) {
                log.warning("LogHandler was already registered: " + ht);
                return;
            }
            handlers.add(ht);
        }

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
    public int getMessageCount() {
        return messageCount.get();
    }

    /**
     * Hook which is called when the select loop has finished.
     */
    public void selectLoopHook(boolean before) {
        if (batchedMode.get()) {
            flushBatch(stealBatch());
        }
    }
}
