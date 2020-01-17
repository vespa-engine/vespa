// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private final boolean loadBalancerActive;
    private final GlobalRouting globalRouting;

    /** DO NOT USE. Public for serialization purposes */
    public Status(boolean loadBalancerActive, GlobalRouting globalRouting) {
        this.loadBalancerActive = loadBalancerActive;
        this.globalRouting = Objects.requireNonNull(globalRouting, "globalRouting must be non-null");
    }

    /** Returns whether the load balancer is active in node repository */
    public boolean loadBalancerActive() {
        return loadBalancerActive;
    }

    /** Return status of global routing */
    public GlobalRouting globalRouting() {
        return globalRouting;
    }

    /** Returns a copy of this with global routing changed */
    public Status with(GlobalRouting globalRouting) {
        return new Status(loadBalancerActive, globalRouting);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Status status = (Status) o;
        return loadBalancerActive == status.loadBalancerActive &&
               globalRouting.equals(status.globalRouting);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loadBalancerActive, globalRouting);
    }

}
