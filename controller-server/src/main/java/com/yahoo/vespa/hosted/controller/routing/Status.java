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

    /** DO NOT USE. Public for serialization purposes */
    public Status(boolean loadBalancerActive) {
        this.loadBalancerActive = loadBalancerActive;
    }

    /** Returns whether the load balancer is active in node repository */
    public boolean loadBalancerActive() {
        return loadBalancerActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Status status = (Status) o;
        return loadBalancerActive == status.loadBalancerActive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(loadBalancerActive);
    }

}
