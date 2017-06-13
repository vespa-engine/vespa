// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * <p>An asynchronous response from the document api.
 * Subclasses of this provide additional response information for particular operations.</p>
 *
 * <p>This is a <i>value object</i>.</p>
 *
 * @author bratseth
 */
public class Response {

    private long requestId;
    private String textMessage = null;
    private boolean success = true;

    /** Creates a successful response containing no information */
    public Response(long requestId) {
        this(requestId, null, true);
    }

    /**
     * Creates a successful response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     */
    public Response(long requestId, String textMessage) {
        this(requestId, textMessage, true);
    }

    /**
     * Creates a response containing a textual message
     *
     * @param textMessage the message to encapsulate in the Response
     * @param success     true if the response represents a successful call
     */
    public Response(long requestId, String textMessage, boolean success) {
        this.requestId = requestId;
        this.textMessage = textMessage;
        this.success = success;
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
    public boolean isSuccess() { return success; }

    public long getRequestId() { return requestId; }

    public int hashCode() {
        return (new Long(requestId).hashCode()) + (textMessage == null ? 0 : textMessage.hashCode()) +
                (success ? 1 : 0);
    }

    public boolean equals(Object o) {
        if (!(o instanceof Response)) {
            return false;
        }
        Response other = (Response) o;

        return requestId == other.requestId && success == other.success && (
                textMessage == null && other.textMessage == null ||
                        textMessage != null && other.textMessage != null && textMessage.equals(other.textMessage));
    }

    public String toString() {
        return "Response " + requestId + (textMessage == null ? "" : textMessage) +
                (success ? " SUCCESSFUL" : " UNSUCCESSFUL");
    }

}
