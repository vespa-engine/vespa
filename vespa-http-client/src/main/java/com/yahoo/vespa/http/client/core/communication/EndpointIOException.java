// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.config.Endpoint;

import java.io.IOException;

/**
 * Class for throwing exception from endpoint.
 *
 * @author dybis
*/
public class EndpointIOException extends IOException {

    private final Endpoint endpoint;
    private static final long serialVersionUID = 29335813211L;

    public EndpointIOException(Endpoint endpoint, String message, Throwable cause) {
        super(message, cause);
        this.endpoint = endpoint;
    }

    /** Returns the endpoint, or null if the failure occurred before this was assigned to a unique endpoint */
    public Endpoint getEndpoint() { return endpoint; }

}
