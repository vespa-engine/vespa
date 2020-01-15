// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.application.EndpointId;

import java.util.Objects;

/**
 * Unique identifier for a global routing table entry (application x endpoint ID).
 *
 * @author mpolden
 */
public class RoutingId {

    private final ApplicationId application;
    private final EndpointId endpointId;

    public RoutingId(ApplicationId application, EndpointId endpointId) {
        this.application = Objects.requireNonNull(application, "application must be non-null");
        this.endpointId = Objects.requireNonNull(endpointId, "endpointId must be non-null");
    }

    public ApplicationId application() {
        return application;
    }

    public EndpointId endpointId() {
        return endpointId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingId that = (RoutingId) o;
        return application.equals(that.application) &&
               endpointId.equals(that.endpointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(application, endpointId);
    }

}
