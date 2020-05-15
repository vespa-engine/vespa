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
    public void ensureWithinAdvertisedLimits(String type, NodeResources requested, ClusterSpec cluster) {
        if (requested.isUnspecified()) return;

        if (requested.memoryGb() < minAdvertisedMemoryGb(cluster.type()))
            illegal(type, "memory", cluster, requested.memoryGb(), minAdvertisedMemoryGb(cluster.type()));
        if (requested.diskGb() < minAdvertisedDiskGb(requested))
            illegal(type, "disk", cluster, requested.diskGb(), minAdvertisedDiskGb(requested));
    }

    /** Returns whether the real resources we'll end up with on a given tenant node are within limits */
    public boolean isWithinRealLimits(Node candidateTenantNode, ClusterSpec cluster) {
        NodeResources realResources = nodeRepository.resourcesCalculator().realResourcesOf(candidateTenantNode, nodeRepository);

        if (realResources.memoryGb() < minRealMemoryGb(cluster.type())) return false;
        if (realResources.diskGb() < minRealDiskGb()) return false;

        return true;
    }

    public NodeResources enlargeToLegal(NodeResources requested, ClusterSpec.Type clusterType) {
        if (requested.isUnspecified()) return requested;

        return requested.withMemoryGb(Math.max(minAdvertisedMemoryGb(clusterType), requested.memoryGb()))
                                  .withDiskGb(Math.max(minAdvertisedDiskGb(requested), requested.diskGb()));
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
        return 4 + minRealDiskGb();
    }

    private double minRealDiskGb() {
        return 6;
    }

    private void illegal(String type, String resource, ClusterSpec cluster, double requested, double minAllowed) {
        String message = String.format(Locale.ENGLISH,
                                       "%s cluster '%s': " + type + " " + resource +
                                       " size is %.2f Gb but must be at least %.2f Gb",
                                       cluster.type().name(), cluster.id().value(), requested, minAllowed);
        throw new IllegalArgumentException(message);
    }

}
