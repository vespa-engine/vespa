// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.Objects;

/**
 * Represents the DNS routing policy for a zone. This takes precedence over of an individual {@link RoutingPolicy}.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class ZoneRoutingPolicy {

    private final ZoneId zone;
    private final GlobalRouting globalRouting;

    public ZoneRoutingPolicy(ZoneId zone, GlobalRouting globalRouting) {
        this.zone = Objects.requireNonNull(zone, "zone must be non-null");
        this.globalRouting = Objects.requireNonNull(globalRouting, "globalRouting must be non-null");
    }

    /** The zone this applies to */
    public ZoneId zone() {
        return zone;
    }

    /** The status of global routing */
    public GlobalRouting globalRouting() {
        return globalRouting;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZoneRoutingPolicy that = (ZoneRoutingPolicy) o;
        return zone.equals(that.zone) &&
               globalRouting.equals(that.globalRouting);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zone, globalRouting);
    }

}
