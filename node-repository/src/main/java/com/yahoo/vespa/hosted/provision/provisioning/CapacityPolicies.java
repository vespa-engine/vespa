// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.function.Supplier;

/**
 * Defines the policies for assigning cluster capacity in various environments
 *
 * @author bratseth
 * @see NodeResourceLimits
 */
public class CapacityPolicies {

    private final Zone zone;
    private final Supplier<Boolean> sharedHosts;

    public CapacityPolicies(NodeRepository nodeRepository) {
        this.zone = nodeRepository.zone();
        this.sharedHosts = PermanentFlags.SHARED_HOST.bindTo(nodeRepository.flagSource()).value()::isEnabled;
    }

    public int decideSize(int requested, Capacity capacity, ClusterSpec cluster, ApplicationId application) {
        if (application.instance().isTester()) return 1;

        ensureRedundancy(requested, cluster, capacity.canFail());
        if (capacity.isRequired()) return requested;
        switch(zone.environment()) {
            case dev : case test : return 1;
            case perf : return Math.min(requested, 3);
            case staging: return requested <= 1 ? requested : Math.max(2, requested / 10);
            case prod : return requested;
            default : throw new IllegalArgumentException("Unsupported environment " + zone.environment());
        }
    }

    public NodeResources decideNodeResources(NodeResources target, Capacity capacity, ClusterSpec cluster) {
        if (target.isUnspecified())
            target = defaultNodeResources(cluster.type());

        if (capacity.isRequired()) return target;

        // Dev does not cap the cpu or network of containers since usage is spotty: Allocate just a small amount exclusively
        if (zone.environment() == Environment.dev && !zone.getCloud().dynamicProvisioning())
            target = target.withVcpu(0.1).withBandwidthGbps(0.1);

        // Allow slow storage in zones which are not performance sensitive
        if (zone.system().isCd() || zone.environment() == Environment.dev || zone.environment() == Environment.test)
            target = target.with(NodeResources.DiskSpeed.any).with(NodeResources.StorageType.any);

        return target;
    }

    private NodeResources defaultNodeResources(ClusterSpec.Type clusterType) {
        if (clusterType == ClusterSpec.Type.admin) {
            if (zone.system() == SystemName.dev) {
                // Use small logserver in dev system
                return new NodeResources(0.1, 1, 10, 0.3);
            }
            return zone.getCloud().dynamicProvisioning() && ! sharedHosts.get() ?
                   new NodeResources(0.5, 4, 50, 0.3) :
                   new NodeResources(0.5, 2, 50, 0.3);
        }

        return zone.getCloud().dynamicProvisioning() ?
                new NodeResources(2.0, 8, 50, 0.3) :
                new NodeResources(1.5, 8, 50, 0.3);
    }

    /**
     * Whether or not the nodes requested can share physical host with other applications.
     * A security feature which only makes sense for prod.
     */
    public boolean decideExclusivity(Capacity capacity, boolean requestedExclusivity) {
        return requestedExclusivity && (capacity.isRequired() || zone.environment() == Environment.prod);
    }

    /**
     * Throw if the node count is 1 for container and content clusters and we're in a production zone
     *
     * @throws IllegalArgumentException if only one node is requested and we can fail
     */
    private void ensureRedundancy(int nodeCount, ClusterSpec cluster, boolean canFail) {
        if (canFail &&
            nodeCount == 1 &&
            requiresRedundancy(cluster.type()) &&
            zone.environment().isProduction())
            throw new IllegalArgumentException("Deployments to prod require at least 2 nodes per cluster for redundancy. Not fulfilled for " + cluster);
    }

    private static boolean requiresRedundancy(ClusterSpec.Type clusterType) {
        return clusterType.isContent() || clusterType.isContainer();
    }

}
