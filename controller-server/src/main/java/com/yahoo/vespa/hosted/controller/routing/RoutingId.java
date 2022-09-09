// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;

import java.util.Objects;

/**
 * Unique identifier for a instance routing table entry (instance x endpoint ID).
 *
 * @author mpolden
 */
public record RoutingId(ApplicationId instance,
                        EndpointId endpointId,
                        TenantAndApplicationId application) {

    public RoutingId {
        Objects.requireNonNull(instance, "application must be non-null");
        Objects.requireNonNull(endpointId, "endpointId must be non-null");
        Objects.requireNonNull(application, "application must be non-null");
    }

    @Override
    public String toString() {
        return "routing id for " + endpointId + " of " + instance;
    }

    public static RoutingId of(ApplicationId instance, EndpointId endpoint) {
        return new RoutingId(instance, endpoint, TenantAndApplicationId.from(instance));
    }

}
