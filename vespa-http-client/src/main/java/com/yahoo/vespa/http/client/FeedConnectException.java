// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Endpoint;

/**
 * An exception thrown when the client is unable to connect to a feed endpoint.
 *
 * @author bjorncs
 */
public class FeedConnectException extends FeedEndpointException {

    public FeedConnectException(Throwable cause, Endpoint endpoint) {
        super(createMessage(cause, endpoint), cause, endpoint);
    }

    private static String createMessage(Throwable cause, Endpoint endpoint) {
        return String.format("Handshake to endpoint '%s:%d' failed: %s",
                             endpoint.getHostname(),
                             endpoint.getPort(),
                             cause.getMessage());
    }
}
