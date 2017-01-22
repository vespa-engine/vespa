// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;

import java.util.Optional;

/**
 * Defines the policies for assigning cluster capacity in various environments
 *
 * @author bratseth
 */
public class CapacityPolicies {

    private final Zone zone;
    private final NodeFlavors flavors;

    public CapacityPolicies(Zone zone, NodeFlavors flavors) {
        this.zone = zone;
        this.flavors = flavors;
    }

    /** provides capacity defaults for various environments */
    public int decideSize(Capacity requestedCapacity) {
        int requestedNodes = requestedCapacity.nodeCount();
        if (requestedCapacity.isRequired()) return requestedNodes;

        switch(zone.environment()) {
            case dev : case test : return 1;
            case perf : return Math.min(requestedCapacity.nodeCount(), 3);
            case staging: return requestedNodes <= 1 ? requestedNodes : Math.max(2, requestedNodes / 10);
            case prod : return ensureRedundancy(requestedCapacity.nodeCount());
            default : throw new IllegalArgumentException("Unsupported environment " + zone.environment());
        }
    }

    public Flavor decideFlavor(Capacity requestedCapacity, ClusterSpec cluster) {
        // for now, always use requested docker flavor when requested
        final Optional<String> requestedFlavor = requestedCapacity.flavor();
        if (requestedFlavor.isPresent() &&
                flavors.getFlavorOrThrow(requestedFlavor.get()).getType() == Flavor.Type.DOCKER_CONTAINER)
            return flavors.getFlavorOrThrow(requestedFlavor.get());

        switch(zone.environment()) {
            case dev : case test : case staging : return flavors.getFlavorOrThrow(zone.defaultFlavor(cluster.type()));
            default : return flavors.getFlavorOrThrow(requestedFlavor.orElse(zone.defaultFlavor(cluster.type())));
        }
    }

    /**
     * Throw if the node count is 1
     *
     * @return the argument node count
     * @throws IllegalArgumentException if only one node is requested
     */
    private int ensureRedundancy(int nodeCount) {
        // TODO: Reactivate this check when we have sufficient capacity in ap-northeast
        // if (nodeCount == 1)
        //    throw new IllegalArgumentException("Deployments to prod require at least 2 nodes per cluster for redundancy");
        return nodeCount;
    }

}
