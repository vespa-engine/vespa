// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * Common class for all visitor responses. All visitor responses have ack tokens that must be ack'ed.
 *
 * @author Håkon Humberset
 */
public class VisitorResponse {
    private AckToken token;

    /**
     * Creates visitor response containing an ack token.
     *
     * @param token the ack token
     */
    public VisitorResponse(AckToken token) {
        this.token = token;
    }

    /** @return The ack token. */
    public AckToken getAckToken() { return token; }
}
