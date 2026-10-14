// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.NoHttpResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

/**
 * Classifies exceptions from HTTP requests to other nodes.
 *
 * @author hmusum
 */
public class ConnectionFailures {

    private ConnectionFailures() {}

    /**
     * Returns true if the exception means that we could not connect to the host or that the host
     * closed the connection without responding, typically because it is restarting, down or retired.
     */
    public static boolean isConnectionFailure(IOException e) {
        return e instanceof ConnectException
               || e instanceof ConnectTimeoutException
               || e instanceof UnknownHostException
               || e instanceof NoRouteToHostException
               || e instanceof NoHttpResponseException;
    }

}
