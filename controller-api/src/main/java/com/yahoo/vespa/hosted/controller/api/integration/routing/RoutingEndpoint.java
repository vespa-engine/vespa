// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

/**
 * @author smorgrav
 */
public class RoutingEndpoint {

    private final boolean isGlobal;
    private final String endpoint;
    private final String hostname;

    public RoutingEndpoint(String endpoint, boolean isGlobal) {
        this.endpoint = endpoint;
        this.hostname = null;
        this.isGlobal = isGlobal;
    }

    public RoutingEndpoint(String endpoint, String hostname, boolean isGlobal) {
        this.endpoint = endpoint;
        this.hostname = hostname;
        this.isGlobal = isGlobal;
    }

    /** @return True if the endpoint is global */
    public boolean isGlobal() {
        return isGlobal;
    }

    /* @return The URI for the endpoint */
    public String getEndpoint() {
        return endpoint;
    }

    /** @return The hostname for this endpoint */
    public String getHostname() {
        return hostname;
    }
}
