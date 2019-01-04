// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

/**
 * @author smorgrav
 */
public class RoutingEndpoint {

    private final boolean isGlobal;
    private final String endpoint;
    private final String hostname;
    private final String upstreamName;

    public RoutingEndpoint(String endpoint, String hostname, boolean isGlobal, String upstreamName) {
        this.endpoint = endpoint;
        this.hostname = hostname;
        this.isGlobal = isGlobal;
        this.upstreamName = upstreamName;
    }

    /** Whether this is a global endpoint */
    public boolean isGlobal() {
        return isGlobal;
    }

    /** URL for this endpoint */
    public String endpoint() {
        return endpoint;
    }

    /** First hostname for an upstream behind this endpoint */
    public String hostname() {
        return hostname;
    }

    /** The upstream name of this endpoint */
    public String upstreamName() {
        return upstreamName;
    }

}
