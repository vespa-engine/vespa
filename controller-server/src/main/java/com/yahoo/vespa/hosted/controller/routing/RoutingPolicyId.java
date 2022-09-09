// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.routing;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;

import java.util.Objects;

/**
 * Unique identifier for a {@link RoutingPolicy}.
 *
 * @author mpolden
 */
public record RoutingPolicyId(ApplicationId owner, ClusterSpec.Id cluster, ZoneId zone) {

    public RoutingPolicyId {
        Objects.requireNonNull(owner, "owner must be non-null");
        Objects.requireNonNull(cluster, "cluster must be non-null");
        Objects.requireNonNull(zone, "zone must be non-null");
    }

    /** The deployment this applies to */
    public DeploymentId deployment() {
        return new DeploymentId(owner, zone);
    }

    @Override
    public String toString() {
        return "routing policy for " + cluster + ", in " + zone + ", owned by " + owner;
    }

}
