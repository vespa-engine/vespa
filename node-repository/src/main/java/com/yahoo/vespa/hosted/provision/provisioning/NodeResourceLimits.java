// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.util.Locale;

/**
 * Defines the resource limits for nodes in various zones
 *
 * @author bratseth
 * @see CapacityPolicies
 */
public class NodeResourceLimits {

    private final NodeRepository nodeRepository;

    public NodeResourceLimits(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /** Validates the resources applications ask for (which are in "advertised" resource space) */
    public void ensureWithinAdvertisedLimits(String type, NodeResources requested, ApplicationId applicationId, ClusterSpec cluster) {
        if (! requested.vcpuIsUnspecified() && requested.vcpu() < minAdvertisedVcpu(applicationId, cluster))
            illegal(type, "vcpu", "", cluster, requested.vcpu(), minAdvertisedVcpu(applicationId, cluster));
        if (! requested.memoryGbIsUnspecified() && requested.memoryGb() < minAdvertisedMemoryGb(cluster))
            illegal(type, "memoryGb", "Gb", cluster, requested.memoryGb(), minAdvertisedMemoryGb(cluster));
        if (! requested.diskGbIsUnspecified() && requested.diskGb() < minAdvertisedDiskGb(requested, cluster.isExclusive()))
            illegal(type, "diskGb", "Gb", cluster, requested.diskGb(), minAdvertisedDiskGb(requested, cluster.isExclusive()));
    }

    /** Returns whether the real resources we'll end up with on a given tenant node are within limits */
    public boolean isWithinRealLimits(NodeCandidate candidateNode, ApplicationId applicationId, ClusterSpec cluster) {
        if (candidateNode.type() != NodeType.tenant) return true; // Resource limits only apply to tenant nodes
        return isWithinRealLimits(nodeRepository.resourcesCalculator().realResourcesOf(candidateNode, nodeRepository),
                                  applicationId, cluster);
    }

    /** Returns whether the real resources we'll end up with on a given tenant node are within limits */
    public boolean isWithinRealLimits(NodeResources realResources, ApplicationId applicationId, ClusterSpec cluster) {
        if (realResources.isUnspecified()) return true;

        if (realResources.vcpu() < minRealVcpu(applicationId, cluster)) return false;
        if (realResources.memoryGb() < minRealMemoryGb(cluster)) return false;
        if (realResources.diskGb() < minRealDiskGb()) return false;
       return true;
    }

    public NodeResources enlargeToLegal(NodeResources requested, ApplicationId applicationId, ClusterSpec cluster, boolean exclusive) {
        if (requested.isUnspecified()) return requested;

        return requested.withVcpu(Math.max(minAdvertisedVcpu(applicationId, cluster), requested.vcpu()))
                        .withMemoryGb(Math.max(minAdvertisedMemoryGb(cluster), requested.memoryGb()))
                        .withDiskGb(Math.max(minAdvertisedDiskGb(requested, exclusive), requested.diskGb()));
    }

    private double minAdvertisedVcpu(ApplicationId applicationId, ClusterSpec cluster) {
        if (cluster.type() == ClusterSpec.Type.admin) return 0.1;
        if (zone().environment().isProduction() && ! zone().system().isCd() &&
                nodeRepository.exclusiveAllocation(cluster) && ! applicationId.instance().isTester()) return 2;
        if (zone().environment().isProduction() && cluster.type().isContent()) return 1.0;
        if (zone().environment() == Environment.dev && ! nodeRepository.exclusiveAllocation(cluster)) return 0.1;
        return 0.5;
    }

    private double minAdvertisedMemoryGb(ClusterSpec cluster) {
        if (cluster.type() == ClusterSpec.Type.admin) return 1;
        return 4;
    }

    private double minAdvertisedDiskGb(NodeResources requested, boolean exclusive) {
        return minRealDiskGb() + reservedDiskSpaceGb(requested.storageType(), exclusive);
    }

    // Note: Assumes node type 'host'
    private long reservedDiskSpaceGb(NodeResources.StorageType storageType, boolean exclusive) {
        if (storageType == NodeResources.StorageType.local && ! zone().cloud().allowHostSharing())
            return nodeRepository.resourcesCalculator().reservedDiskSpaceInBase2Gb(NodeType.host, ! exclusive);
        else
            return 4;
    }

    private double minRealVcpu(ApplicationId applicationId, ClusterSpec cluster) {
        return minAdvertisedVcpu(applicationId, cluster);
    }

    private double minRealMemoryGb(ClusterSpec cluster) {
        if (cluster.type() == ClusterSpec.Type.admin) return 0.95; // TODO: Increase to 1.05 after March 2023
        return 2.3;
    }

    private double minRealDiskGb() { return 6; }

    private Zone zone() { return nodeRepository.zone(); }

    private void illegal(String type, String resource, String unit, ClusterSpec cluster, double requested, double minAllowed) {
        if ( ! unit.isEmpty())
            unit = " " + unit;
        String message = String.format(Locale.ENGLISH,
                                       "%s cluster '%s': " + type + " " + resource +
                                       " size is %.2f%s but must be at least %.2f%s",
                                       cluster.type().name(), cluster.id().value(), requested, unit, minAllowed, unit);
        throw new IllegalArgumentException(message);
    }

}
