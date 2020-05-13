// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.Locale;

/**
 * Defines the resource limits for nodes in various zones
 *
 * @author bratseth
 */
public class NodeResourceLimits {

    private final NodeRepository nodeRepository;

    public NodeResourceLimits(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /** Validates the resources applications ask for (which are in "advertised" resource space) */
    public void ensureWithinAdvertisedLimits(NodeResources requestedResources, ClusterSpec cluster) {
        double minMemoryGb = minAdvertisedMemoryGb(cluster.type());
        if (requestedResources.memoryGb() < minMemoryGb)
            throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                                                             "Must specify at least %.2f Gb of memory for %s cluster '%s', was: %.2f Gb",
                                                             minMemoryGb, cluster.type().name(), cluster.id().value(), requestedResources.memoryGb()));

        double minDiskGb = minAdvertisedDiskGb(requestedResources);
        if (requestedResources.diskGb() < minDiskGb)
            throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                                                             "Must specify at least %.2f Gb of disk for %s cluster '%s', was: %.2f Gb",
                                                             minDiskGb, cluster.type().name(), cluster.id().value(), requestedResources.diskGb()));
    }

    /** Returns whether the real resources we'll end up with on a given tenant node are within limits */
    public boolean isWithinRealLimits(Node candidateTenantNode, ClusterSpec cluster) {
        NodeResources realResources = nodeRepository.resourcesCalculator().realResourcesOf(candidateTenantNode, nodeRepository);

        if (realResources.memoryGb() < minRealMemoryGb(cluster.type())) return false;
        if (realResources.diskGb() < minRealDiskGb()) return false;

        return true;
    }

    public NodeResources enlargeToLegal(NodeResources advertisedResources, ClusterSpec.Type clusterType) {
        return advertisedResources.withMemoryGb(Math.max(minAdvertisedMemoryGb(clusterType), advertisedResources.memoryGb()));
    }

    private double minAdvertisedMemoryGb(ClusterSpec.Type clusterType) {
        if (nodeRepository.zone().system() == SystemName.dev) return 1; // Allow small containers in dev system
        if (clusterType == ClusterSpec.Type.admin) return 2;
        return 4;
    }

    private double minRealMemoryGb(ClusterSpec.Type clusterType) {
        return minAdvertisedMemoryGb(clusterType) - 0.7;
    }

    private double minAdvertisedDiskGb(NodeResources requested) {
        if (requested.storageType() == NodeResources.StorageType.local
            && nodeRepository.zone().getCloud().dynamicProvisioning()) {
            if (nodeRepository.zone().system() == SystemName.Public)
                return 10 + minRealDiskGb();
            else
                return 55 + minRealDiskGb();
        }
        return minRealDiskGb();
    }

    private double minRealDiskGb() {
        return 10;
    }

}
