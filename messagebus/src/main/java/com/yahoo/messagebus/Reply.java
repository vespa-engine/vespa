// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * <p>A reply is a response to a message that has been sent throught the messagebus. No reply will ever exist without a
 * corresponding message. There are no error-replies defined, as errors can instead piggyback any reply by the {@link
 * #errors} member variable.</p>
 *
 * @author Simon Thoresen Hult
 */
public abstract class Reply extends Routable {

    private double retryDelay = -1.0;
    private Message msg = null;
    private List<Error> errors = new ArrayList<>();

    @Override
    public void swapState(Routable rhs) {
        super.swapState(rhs);
        if (rhs instanceof Reply) {
            Reply reply = (Reply)rhs;

            double retryDelay = this.retryDelay;
            this.retryDelay = reply.retryDelay;
            reply.retryDelay = retryDelay;

            Message msg = this.msg;
            this.msg = reply.msg;
            reply.msg = msg;

            List<Error> errors = this.errors;
            this.errors = reply.errors;
            reply.errors = errors;
        }
    }

    /**
     * <p>Returns the message to which this is a reply.</p>
     *
     * @return The message.
     */
    public Message getMessage() {
        return msg;
    }

    /**
     * <p>Sets the message to which this is a reply. Although it might seem very bogus to allow such an accessor, it is
     * necessary since we allow an empty constructor.</p>
     *
     * @param msg The message to which this is a reply.
     */
    public void setMessage(Message msg) {
        this.msg = msg;
    }

    /**
     * <p>Returns whether or not this reply contains any errors.</p>
     *
     * @return True if there are errors, false otherwise.
     */
    public boolean hasErrors() {
        return errors.size() > 0;
    }

    /**
     * <p>Returns whether or not this reply contains any fatal errors.</p>
     *
     * @return True if it contains fatal errors.
     */
    public boolean hasFatalErrors() {
        for (Error error : errors) {
            if (error.getCode() >= ErrorCode.FATAL_ERROR) {
                return true;
            }
        }
        return false;
    }

    /**
     * <p>Returns the error at the given position.</p>
     *
     * @param i The index of the error to return.
     * @return The error at the given index.
     */
    public Error getError(int i) {
        return errors.get(i);
    }

    /**
     * <p>Returns the number of errors that this reply contains.</p>
     *
     * @return The number of replies.
     */
    public int getNumErrors() {
        return errors.size();
    }

    /**
     * <p>Add an error to this reply. This method will also trace the error as long as there is any tracing
     * enabled.</p>
     *
     * @param error The error object to add.
     */
    public void addError(Error error) {
        errors.add(error);
        getTrace().trace(TraceLevel.ERROR, error.toString());
    }

    /**
     * <p>Returns the retry request of this reply. This can be set using {@link #setRetryDelay} and is an instruction to
     * the resender logic of message bus on how to perform the retry. If this value is anything other than a negative
     * number, it instructs the resender to disregard all configured resending attributes and instead act according to
     * this value.</p>
     *
     * @return The retry request.
     */
    public double getRetryDelay() {
        return retryDelay;
    }

    /**
     * <p>Sets the retry delay request of this reply. If this is a negative number, it will use the defaults configured
     * in the source session.</p>
     *
     * @param retryDelay The retry request.
     */
    public void setRetryDelay(double retryDelay) {
        this.retryDelay = retryDelay;
    }

    /**
     * Retrieves a (read only) stream of the errors in this reply
     */
    public Stream<Error> getErrors() {
        return errors.stream();
    }

    /**
     * Retrieves a set of integer error codes
     */
    public Set<Integer> getErrorCodes() {
        Set<Integer> errorCodes = new HashSet<>();
        for (Error error : errors) {
            errorCodes.add(error.getCode());
        }
        return errorCodes;
    }

}
