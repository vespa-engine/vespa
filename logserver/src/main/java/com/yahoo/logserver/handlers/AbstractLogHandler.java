// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers;

import java.util.Iterator;
import java.util.List;

import com.yahoo.log.LogMessage;
import com.yahoo.logserver.filter.LogFilter;

/**
 * This abstract class is the one you would usually want to
 * extend when you are writing a LogHandler since it takes care
 * of quite a bit of tedious work for you (log message counting,
 * handling of lists of messages versus single instances etc).
 *
 * @author Bjorn Borud
 */
public abstract class AbstractLogHandler implements LogHandler {
    private long count = 0;
    private long filtered = 0;
    private LogFilter filter = null;
    private String name;

    /**
     * This is the entry point for each log handler.  Takes care
     * of calling the actual doHandle() method.  Provided to make
     * it possible to extend what a handler does in a uniform way.
     *
     * @param msg The message we are about to handle
     */
    public final void handle(LogMessage msg) {
        if ((filter != null) && (! filter.isLoggable(msg))) {
            filtered++;
            return;
        }

        if (doHandle(msg)) {
            count++;
        }
    }

    /**
     * Handle a list of LogMessage instances
     *
     * @param messages List of LogMessage instances.
     */
    public final void handle(List<LogMessage> messages) {
        for (LogMessage l : messages) {
            handle(l);
        }
    }

    /**
     * Set LogFilter for this LogHandler.  If the LogFilter is
     * <code>null</code>, filtering has in effect been turned
     * off.
     *
     * @param filter The filter to be used for this handler
     */
    public void setLogFilter(LogFilter filter) {
        this.filter = filter;
    }

    /**
     * @return Returns the log filter for this handler or
     * <code>null</code> if no filter is in effect.
     */
    public LogFilter getLogFilter() {
        return filter;
    }

    /**
     * Returns the internal counter which keeps track of the number
     * of times doHandle has been called.
     *
     * @return Returns the number of times doHandle has been called.
     */
    public final long getCount() {
        return count;
    }

    public String getName() {
        if (name == null) {
            String n = this.getClass().getName();
            int x = n.lastIndexOf('.');
            if (x != - 1) {
                n = n.substring(x + 1);
            }
            name = n;
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The method which actually handles the log message and
     * does something to it.  This is the one you wish to
     * override when you write a new handler.
     * <p>
     * <em>
     * If your handle method is slow you should document this fact
     * so that decisions can be made with regard to configuration.
     * </em>
     *
     * @param msg The LogMessage we are about to handle
     * @return Returns <code>true</code> if the message was
     * handled and <code>false</code> if it was ignored.
     */
    public abstract boolean doHandle(LogMessage msg);

    /**
     * Flush LogMessages.
     */
    public abstract void flush();

    /**
     * Close this loghandler.  After a loghandler is closed calling
     * the #handle() has undefined behavior, but it should be assumed
     * that log messages will be silently dropped.
     * <p>
     * #close() usually implies #flush() but don't bet on it.
     */
    public abstract void close();

    /**
     * Force implementation of (hopefully meaningful) toString()
     */
    public abstract String toString();
}
