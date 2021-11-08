// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.Objects;

/**
 * Represents the DNS routing policy for a zone. This takes precedence over of a deployment-specific
 * {@link RoutingPolicy}.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class ZoneRoutingPolicy {

    private final ZoneId zone;
    private final RoutingStatus routingStatus;

    public ZoneRoutingPolicy(ZoneId zone, RoutingStatus routingStatus) {
        this.zone = Objects.requireNonNull(zone, "zone must be non-null");
        this.routingStatus = Objects.requireNonNull(routingStatus, "globalRouting must be non-null");
    }

    /** The zone this applies to */
    public ZoneId zone() {
        return zone;
    }

    /** Routing status of this policy */
    public RoutingStatus routingStatus() {
        return routingStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZoneRoutingPolicy that = (ZoneRoutingPolicy) o;
        return zone.equals(that.zone) &&
               routingStatus.equals(that.routingStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zone, routingStatus);
    }

}
