// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.JacksonFlag;

import java.util.Arrays;
import java.util.Optional;

/**
 * Defines the policies for assigning cluster capacity in various environments
 *
 * @author bratseth
 */
public class CapacityPolicies {

    private final Zone zone;
    private final JacksonFlag<com.yahoo.vespa.flags.custom.NodeResources> defaultResourcesFlag;

    public CapacityPolicies(Zone zone, FlagSource flagSource) {
        this.zone = zone;
        this.defaultResourcesFlag = Flags.DEFAULT_RESOURCES.bindTo(flagSource);
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
        NodeResources resources = requestedResources
                .or(() -> flagNodeResources(cluster.type()))
                .orElse(defaultNodeResources(cluster.type()));

        // Allow slow disks in zones which are not performance sensitive
        if (zone.system().isCd() || zone.environment() == Environment.dev || zone.environment() == Environment.test)
            resources = resources.withDiskSpeed(NodeResources.DiskSpeed.any);

        // Dev does not cap the cpu of containers since usage is spotty: Allocate just a small amount exclusively
        if (zone.environment() == Environment.dev)
            resources = resources.withVcpu(0.1);

        return resources;
    }

    private Optional<NodeResources> flagNodeResources(ClusterSpec.Type clusterType) {
        return Optional.ofNullable(defaultResourcesFlag.with(FetchVector.Dimension.CLUSTER_TYPE, clusterType.name()).value())
                .map(r -> new NodeResources(r.vcpu(), r.memoryGb(), r.diskGb(), r.bandwidthGbps(), NodeResources.DiskSpeed.valueOf(r.diskSpeed())));
    }

    private NodeResources defaultNodeResources(ClusterSpec.Type clusterType) {
        if (clusterType == ClusterSpec.Type.admin)
            return new NodeResources(0.5, 2, 50, 0.3);

        return new NodeResources(1.5, 8, 50, 0.3);
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
