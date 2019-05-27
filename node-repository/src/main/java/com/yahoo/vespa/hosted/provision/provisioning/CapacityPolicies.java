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

    public NodeResources decideNodeResources(Optional<NodeResources> requestedResources, ClusterSpec cluster) {
        NodeResources resources = specifiedOrDefaultNodeResources(requestedResources, cluster);

        if (resources.allocateByLegacyName()) return resources; // Modification not possible

        // Allow slow disks in zones which are not performance sensitive
        if (zone.system() == SystemName.cd || zone.environment() == Environment.dev || zone.environment() == Environment.test)
            resources = resources.withDiskSpeed(NodeResources.DiskSpeed.any);

        // Dev does not cap the cpu of containers since usage is spotty: Allocate just a small amount exclusively
        if (zone.environment() == Environment.dev)
            resources = resources.withVcpu(0.1);

        return resources;
    }

    private NodeResources specifiedOrDefaultNodeResources(Optional<NodeResources> requestedResources, ClusterSpec cluster) {
        if (requestedResources.isPresent() && ! requestedResources.get().allocateByLegacyName())
            return requestedResources.get();

        if (requestedResources.isEmpty())
            return defaultNodeResources();

        // Flavor is specified and is allocateByLegacyName: Handle legacy flavor specs
        if (zone.system() == SystemName.cd)
            return flavors.exists(requestedResources.get().legacyName().get()) ? requestedResources.get() : defaultNodeResources();
        else {
            switch (zone.environment()) {
                case dev: case test: case staging: return defaultNodeResources();
                default:
                    flavors.getFlavorOrThrow(requestedResources.get().legacyName().get()); // verify existence
                    // Return this spec containing the legacy flavor name, not the flavor's capacity object
                    // which describes the flavors capacity, as the point of legacy allocation is to match
                    // by name, not by resources
                    return requestedResources.get();
            }
        }
    }

    private NodeResources defaultNodeResources() {
        /*
CD_DEV_CD_US_CENTRAL_1: d_2_8_50
CD_DEV_CD_US_WEST_1: d_1_4_50
CD_PROD_AWS_US_EAST_1A: d_2_8_50
CD_PROD_CD_US_CENTRAL_1: d_2_8_50
CD_PROD_CD_US_WEST_1: d_2_8_50
CD_TEST_CD_US_CENTRAL_1: d_4_4_50, admin: d_1_3_50
CD_STAGING_CD_US_CENTRAL_1: d_4_4_50, admin: d_1_3_50
MAIN_DEV_AWS_US_EAST_2A: d_2_8_50
MAIN_DEV_US_EAST_1: d_2_8_50
MAIN_PERF_US_EAST_3: d_2_8_50
MAIN_PROD_AWS_US_EAST_1A: d_2_8_50
MAIN_PROD_AWS_US_EAST_1B: d_2_8_50
MAIN_PROD_AWS_US_WEST_2A: d_2_8_50
MAIN_PROD_US_EAST_3: d_2_8_50
MAIN_TEST_US_EAST_1: d_2_8_50
MAIN_PROD_US_WEST_1: d_2_8_50
MAIN_PROD_US_CENTRAL_1: d_2_8_50
MAIN_PROD_EU_WEST_1: d_2_8_50
MAIN_PROD_AP_NORTHEAST_1: d_2_8_50
MAIN_PROD_AP_NORTHEAST_2: d_2_8_50
MAIN_STAGING_US_EAST_3: d_2_8_50
MAIN_PROD_AP_SOUTHEAST_1: d_2_8_50
PUBLIC_CD_PROD_AWS_US_EAST_1C: d_2_8_50
PUBLIC_CD_TEST_AWS_US_EAST_1C: d_2_8_50, admin: d_1_3_50
PUBLIC_CD_STAGING_AWS_US_EAST_1C: d_2_8_50, admin: d_1_3_50
VAAS_DEV_AWS_US_EAST_1B: d_2_8_50
         */

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
