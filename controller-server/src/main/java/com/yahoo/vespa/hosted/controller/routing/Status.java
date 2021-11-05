// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import java.util.Objects;

/**
 * Represents the status of a routing policy.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class Status {

    private final boolean active;
    private final RoutingStatus routingStatus;

    /** DO NOT USE. Public for serialization purposes */
    public Status(boolean active, RoutingStatus routingStatus) {
        this.active = active;
        this.routingStatus = Objects.requireNonNull(routingStatus, "globalRouting must be non-null");
    }

    /** Returns whether this is considered active according to the load balancer status */
    public boolean isActive() {
        return active;
    }

    /** Return status of routing */
    public RoutingStatus routingStatus() {
        return routingStatus;
    }

    /** Returns a copy of this with routing status changed */
    public Status with(RoutingStatus routingStatus) {
        return new Status(active, routingStatus);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Status status = (Status) o;
        return active == status.active &&
               routingStatus.equals(status.routingStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(active, routingStatus);
    }

}
