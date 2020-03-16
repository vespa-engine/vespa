// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.routing;

import java.util.Objects;

/**
 * @author smorgrav
 */
// TODO(mpolden): Remove together with RoutingGenerator and its implementations
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingEndpoint that = (RoutingEndpoint) o;
        return isGlobal == that.isGlobal &&
               endpoint.equals(that.endpoint) &&
               hostname.equals(that.hostname) &&
               upstreamName.equals(that.upstreamName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isGlobal, endpoint, hostname, upstreamName);
    }

}
