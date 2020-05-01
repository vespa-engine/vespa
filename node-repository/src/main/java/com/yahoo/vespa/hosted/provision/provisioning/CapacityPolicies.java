// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;

import java.util.Locale;

/**
 * Defines the policies for assigning cluster capacity in various environments
 *
 * @author bratseth
 */
public class CapacityPolicies {

    private final Zone zone;

    private final NodeResourceLimits nodeResourceLimits;

    /* Deployments must match 1-to-1 the advertised resources of a physical host */
    private final boolean isUsingAdvertisedResources;

    public CapacityPolicies(Zone zone) {
        this.zone = zone;
        this.nodeResourceLimits = new NodeResourceLimits(zone);
        this.isUsingAdvertisedResources = zone.cloud().value().equals("aws");
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

    public NodeResources decideNodeResources(NodeResources requested, Capacity capacity, ClusterSpec cluster) {
        if (requested == NodeResources.unspecified)
            requested = defaultNodeResources(cluster.type());
        ensureSufficientResources(requested, cluster);

        if (capacity.isRequired()) return requested;

        // Allow slow storage in zones which are not performance sensitive
        if (zone.system().isCd() || zone.environment() == Environment.dev || zone.environment() == Environment.test)
            requested = requested.with(NodeResources.DiskSpeed.any).with(NodeResources.StorageType.any);

        // Dev does not cap the cpu of containers since usage is spotty: Allocate just a small amount exclusively
        // Do not cap in AWS as hosts are allocated on demand and 1-to-1, so the node can use the entire host
        if (zone.environment() == Environment.dev && !zone.region().value().contains("aws-"))
            requested = requested.withVcpu(0.1);

        return requested;
    }

    private void ensureSufficientResources(NodeResources resources, ClusterSpec cluster) {
        double minMemoryGb = nodeResourceLimits.minMemoryGb(cluster.type());
        if (resources.memoryGb() >= minMemoryGb) return;
        throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                "Must specify at least %.2f Gb of memory for %s cluster '%s', was: %.2f Gb",
                minMemoryGb, cluster.type().name(), cluster.id().value(), resources.memoryGb()));
    }

    private NodeResources defaultNodeResources(ClusterSpec.Type clusterType) {
        if (clusterType == ClusterSpec.Type.admin) {
            if (zone.system() == SystemName.dev) {
                // Use small logserver in dev system
                return new NodeResources(0.1, 1, 10, 0.3);
            }
            return isUsingAdvertisedResources ?
                    new NodeResources(0.5, 4, 50, 0.3) :
                    new NodeResources(0.5, 2, 50, 0.3);
        }

        return isUsingAdvertisedResources ?
                new NodeResources(2.0, 8, 50, 0.3) :
                new NodeResources(1.5, 8, 50, 0.3);
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
