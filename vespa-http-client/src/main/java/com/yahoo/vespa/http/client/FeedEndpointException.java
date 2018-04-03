// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import com.yahoo.vespa.http.client.config.Endpoint;

/**
 * An exception type for endpoint specific errors.
 *
 * @see FeedConnectException
 * @see FeedProtocolException
 * @author bjorncs
 */
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
