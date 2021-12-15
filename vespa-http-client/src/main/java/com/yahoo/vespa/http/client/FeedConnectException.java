// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Endpoint;

/**
 * An exception thrown when the client is unable to connect to a feed endpoint.
 *
 * @author bjorncs
 * @deprecated Vespa-http-client will be removed in Vespa 8. It's replaced by <a href="https://docs.vespa.ai/en/vespa-feed-client.html">vespa-feed-client</a>
 */
@Deprecated
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
