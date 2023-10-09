// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers;

import com.yahoo.log.LogMessage;

import java.util.List;

/**
 * The LogHandler interface defines the interface used for all
 * parts of the logserver which consume log messages.
 *
 * @author Bjorn Borud
 */
public interface LogHandler {
    /**
     * This is the entry point for the log handling.  This method
     * should return as quickly as possible in implementations
     * so if you need to initiate time consuming processing you
     * should look into some design alternatives.
     *
     * @param msg The log message
     */
    void handle(LogMessage msg);

    /**
     * Instead of taking a single log message, this method can take
     * a List of them.  The List abstraction was chosen because the
     * order needs to be preserved.
     *
     * @param messages a List containing zero or more LogMessage
     *                 instances.
     */
    void handle(List<LogMessage> messages);

    /**
     * Any log messages received so far should be dealt with
     * before this method returns -- within reason ,of course.
     * (<em>Within reason is loosely defined to be 2-5 seconds</em>)
     */
    void flush();

    /**
     * Signals that we want to end logging and should close down the
     * underlying logging mechanism -- whatever this maps to
     * semantically for the underlying implementation.  After this
     * method has been called it is considered an error to submit more
     * log messages to the handle() methods and an implementation
     * may elect to throw runtime exceptions.
     */
    void close();
}
