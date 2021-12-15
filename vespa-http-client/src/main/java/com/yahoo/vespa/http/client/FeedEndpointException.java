// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Endpoint;

/**
 * An exception type for endpoint specific errors.
 *
 * @see FeedConnectException
 * @see FeedProtocolException
 * @author bjorncs
 * @deprecated Vespa-http-client will be removed in Vespa 8. It's replaced by <a href="https://docs.vespa.ai/en/vespa-feed-client.html">vespa-feed-client</a>
 */
@Deprecated
public abstract class FeedEndpointException extends RuntimeException {

    private final Endpoint endpoint;

    protected FeedEndpointException(String message, Throwable cause, Endpoint endpoint) {
        super(message, cause);
        this.endpoint = endpoint;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

}
