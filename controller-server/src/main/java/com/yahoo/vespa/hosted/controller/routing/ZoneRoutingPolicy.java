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
public record ZoneRoutingPolicy(ZoneId zone, RoutingStatus routingStatus) {

    public ZoneRoutingPolicy {
        Objects.requireNonNull(zone, "zone must be non-null");
        Objects.requireNonNull(routingStatus, "globalRouting must be non-null");
    }

}
