// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.messagebus.Trace;

import java.util.Objects;

import static com.yahoo.documentapi.Response.Outcome.ERROR;
import static com.yahoo.documentapi.Response.Outcome.SUCCESS;

/**
 * An asynchronous response from the document api.
 * Subclasses of this provide additional response information for particular operations.
 *
 * @author bratseth
 */
public class Response {

    private final long requestId;
    private final String textMessage;
    private final Outcome outcome;
    private final Trace trace;

    /** Creates a successful response containing no information */
    public Response(long requestId) {
        this(requestId, null);
    }

    /**
     * Creates a successful response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     */
    public Response(long requestId, String textMessage) {
        this(requestId, textMessage, SUCCESS);
    }

    /**
     * Creates a response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     * @param outcome     the outcome of the operation
     */
    public Response(long requestId, String textMessage, Outcome outcome) {
        this(requestId, textMessage, outcome, null);
    }

    /**
     * Creates a response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     * @param outcome     the outcome of the operation
     */
    public Response(long requestId, String textMessage, Outcome outcome, Trace trace) {
        this.requestId = requestId;
        this.textMessage = textMessage;
        this.outcome = outcome;
        this.trace = trace;
    }

    /**
     * Returns the text message of this response or null if there is none
     *
     * @return the message, or null
     */
    public String getTextMessage() { return textMessage; }

    /**
     * Returns whether this response encodes a success or a failure
     *
     * @return true if success
     */
    public boolean isSuccess() { return outcome == SUCCESS; }

    /** Returns the outcome of this operation. */
    public Outcome outcome() { return outcome; }

    public long getRequestId() { return requestId; }

    /** Returns the trace of this operation, or null if there is none. */
    public Trace getTrace() { return trace; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof Response)) return false;
        Response response = (Response) o;
        return requestId == response.requestId &&
               Objects.equals(textMessage, response.textMessage) &&
               outcome == response.outcome;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, textMessage, outcome);
    }

    public String toString() {
        return "Response " + requestId + (textMessage == null ? "" : textMessage) + " " + outcome;
    }


    public enum Outcome {

        /** The operation was a success. */
        SUCCESS,

        /** The operation failed due to an unmet test-and-set condition. */
        CONDITION_FAILED,

        /** The operation failed because its target document was not found. */
        NOT_FOUND,

        /** The operation failed because the cluster had insufficient storage to accept it. */
        INSUFFICIENT_STORAGE,

        /** The operation timed out before it reached its destination. */
        TIMEOUT,

        /** The operation failed for some unknown reason. */
        ERROR

    }

}
