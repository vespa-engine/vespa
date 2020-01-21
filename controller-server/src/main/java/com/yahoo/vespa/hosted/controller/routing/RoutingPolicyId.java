// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Objects;

/**
 * Unique identifier for a {@link RoutingPolicy}.
 *
 * @author mpolden
 */
public class RoutingPolicyId {

    private final ApplicationId owner;
    private final ClusterSpec.Id cluster;
    private final ZoneId zone;

    public RoutingPolicyId(ApplicationId owner, ClusterSpec.Id cluster, ZoneId zone) {
        this.owner = Objects.requireNonNull(owner, "owner must be non-null");
        this.cluster = Objects.requireNonNull(cluster, "cluster must be non-null");
        this.zone = Objects.requireNonNull(zone, "zone must be non-null");
    }

    /** The application owning this */
    public ApplicationId owner() {
        return owner;
    }

    /** The zone this applies to */
    public ZoneId zone() {
        return zone;
    }

    /** The cluster this applies to */
    public ClusterSpec.Id cluster() {
        return cluster;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingPolicyId that = (RoutingPolicyId) o;
        return owner.equals(that.owner) &&
               cluster.equals(that.cluster) &&
               zone.equals(that.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, cluster, zone);
    }

}
