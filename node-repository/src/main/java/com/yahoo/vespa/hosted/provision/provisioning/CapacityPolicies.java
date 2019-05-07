// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;

import com.yahoo.config.provision.NodeFlavors;

import java.util.Arrays;
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

    public int decideSize(Capacity requestedCapacity, ClusterSpec.Type clusterType) {
        int requestedNodes = ensureRedundancy(requestedCapacity.nodeCount(), clusterType, requestedCapacity.canFail());
        if (requestedCapacity.isRequired()) return requestedNodes;

        switch(zone.environment()) {
            case dev : case test : return 1;
            case perf : return Math.min(requestedCapacity.nodeCount(), 3);
            case staging: return requestedNodes <= 1 ? requestedNodes : Math.max(2, requestedNodes / 10);
            case prod : return requestedNodes;
            default : throw new IllegalArgumentException("Unsupported environment " + zone.environment());
        }
    }

    public NodeResources decideFlavor(Capacity requestedCapacity, ClusterSpec cluster) {
        Optional<NodeResources> requestedFlavor = requestedCapacity.nodeResources();
        if (requestedFlavor.isPresent() && ! requestedFlavor.get().allocateByLegacyName())
            return requestedFlavor.get();

        NodeResources defaultFlavor = NodeResources.fromLegacyName(zone.defaultFlavor(cluster.type()));
        if (requestedFlavor.isEmpty())
            return defaultFlavor;

        // Flavor is specified and is allocateByLegacyName: Handle legacy flavor specs
        if (zone.system() == SystemName.cd)
            return flavors.exists(requestedFlavor.get().legacyName().get()) ? requestedFlavor.get() : defaultFlavor;
        else {
            switch (zone.environment()) {
                case dev: case test: case staging: return defaultFlavor;
                default:
                    // Check existence of the legacy specified flavor
                    flavors.getFlavorOrThrow(requestedFlavor.get().legacyName().get());
                    // Return this spec containing the legacy flavor name, not the flavor's capacity object
                    // which describes the flavors capacity, as the point of legacy allocation is to match
                    // by name, not by resources
                    return requestedFlavor.get();
            }
        }
    }

    /**
     * Whether or not the nodes requested can share physical host with other applications.
     * A security feature which only makes sense for prod.
     */
    public boolean decideExclusivity(boolean requestedExclusivity) {
        return requestedExclusivity && zone.environment() == Environment.prod;
    }

    /**
     * Throw if the node count is 1 for container and content clusters and we're in a production zone
     *
     * @return the argument node count
     * @throws IllegalArgumentException if only one node is requested and we can fail
     */
    private int ensureRedundancy(int nodeCount, ClusterSpec.Type clusterType, boolean canFail) {
        if (canFail &&
                nodeCount == 1 &&
                Arrays.asList(ClusterSpec.Type.container, ClusterSpec.Type.content).contains(clusterType) &&
                zone.environment().isProduction())
            throw new IllegalArgumentException("Deployments to prod require at least 2 nodes per cluster for redundancy");
        return nodeCount;
    }

}
