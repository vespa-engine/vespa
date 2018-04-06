// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

/**
 * Wrapper for shared constants used by both client and server.
 *
 * @author Steinar Knutsen
 */
public final class Headers {

    private Headers() {
    }

    public static final String TIMEOUT = "X-Yahoo-Feed-Timeout";
    public static final String DRAIN = "X-Yahoo-Feed-Drain";
    public static final String ROUTE = "X-Yahoo-Feed-Route";
    public static final String VERSION = "X-Yahoo-Feed-Protocol-Version";
    public static final String SESSION_ID = "X-Yahoo-Feed-Session-Id";
    public static final String DENY_IF_BUSY = "X-Yahoo-Feed-Deny-If-Busy";
    public static final String DATA_FORMAT = "X-Yahoo-Feed-Data-Format";
    // This value can be used to route the request to a specific server when using
    // several servers. It is a random value that is the same for the whole session.
    public static final String SHARDING_KEY = "X-Yahoo-Feed-Sharding-Key";
    public static final String PRIORITY = "X-Yahoo-Feed-Priority";
    public static final String TRACE_LEVEL = "X-Yahoo-Feed-Trace-Level";

    public static final int HTTP_NOT_ACCEPTABLE = 406;

    // For version 3 of the API
    public static final String CLIENT_ID = "X-Yahoo-Client-Id";
    public static final String OUTSTANDING_REQUESTS = "X-Yahoo-Outstanding-Requests";
    public static final String HOSTNAME = "X-Yahoo-Hostname";
    public static final String SILENTUPGRADE = "X-Yahoo-Silent-Upgrade";

}
