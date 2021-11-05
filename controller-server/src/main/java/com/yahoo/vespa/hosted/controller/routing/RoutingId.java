// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.application.EndpointId;

import java.util.Objects;

/**
 * Unique identifier for a global routing table entry (instance x endpoint ID).
 *
 * @author mpolden
 */
public class RoutingId {

    private final ApplicationId instance;
    private final EndpointId endpointId;

    public RoutingId(ApplicationId instance, EndpointId endpointId) {
        this.instance = Objects.requireNonNull(instance, "instance must be non-null");
        this.endpointId = Objects.requireNonNull(endpointId, "endpointId must be non-null");
    }

    public ApplicationId instance() {
        return instance;
    }

    public EndpointId endpointId() {
        return endpointId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingId that = (RoutingId) o;
        return instance.equals(that.instance) &&
               endpointId.equals(that.endpointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instance, endpointId);
    }

    @Override
    public String toString() {
        return "routing id for " + endpointId + " of " + instance;
    }

    public static RoutingId of(ApplicationId instance, EndpointId endpoint) {
        return new RoutingId(instance, endpoint);
    }

}
