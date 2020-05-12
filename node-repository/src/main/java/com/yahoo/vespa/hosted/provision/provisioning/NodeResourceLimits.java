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

    /** Validates the resources applications ask for */
    public void ensureWithinAdvertisedLimits(NodeResources advertisedResources, ClusterSpec cluster) {
        double minMemoryGb = minAdvertisedMemoryGb(cluster.type());
        if (advertisedResources.memoryGb() >= minMemoryGb) return;
        throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                                                         "Must specify at least %.2f Gb of memory for %s cluster '%s', was: %.2f Gb",
                                                         minMemoryGb, cluster.type().name(), cluster.id().value(), advertisedResources.memoryGb()));
    }

    /** Returns whether the real resources we'll end up with on a given tenant node are within limits */
    public boolean isWithinRealLimits(Node candidateTenantNode, ClusterSpec cluster) {
        NodeResources realResources = nodeRepository.resourcesCalculator().realResourcesOf(candidateTenantNode, nodeRepository);

        if (realResources.memoryGb() < minRealMemoryGb(cluster.type())) return false;

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

}
