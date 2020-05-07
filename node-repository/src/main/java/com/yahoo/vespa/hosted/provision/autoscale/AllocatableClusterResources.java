// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.NodeResourceLimits;

import java.util.List;
import java.util.Optional;

/**
 * @author bratseth
 */
public class AllocatableClusterResources {

    /** The node count in the cluster */
    private final int nodes;

    /** The number of node groups in the cluster */
    private final int groups;

    private final NodeResources realResources;
    private final NodeResources advertisedResources;

    private final ClusterSpec.Type clusterType;

    private final double fulfilment;

    /** Fake allocatable resources from requested capacity */
    public AllocatableClusterResources(ClusterResources requested, ClusterSpec.Type clusterType) {
        this.advertisedResources = requested.nodeResources();
        this.realResources = requested.nodeResources(); // we don't know
        this.nodes = requested.nodes();
        this.groups = requested.groups();
        this.clusterType = clusterType;
        this.fulfilment = 1;
    }

    public AllocatableClusterResources(List<Node> nodes, NodeRepository nodeRepository) {
        this.advertisedResources = nodes.get(0).flavor().resources();
        this.realResources = nodeRepository.resourcesCalculator().realResourcesOf(nodes.get(0), nodeRepository);
        this.nodes = nodes.size();
        this.groups = (int)nodes.stream().map(node -> node.allocation().get().membership().cluster().group()).distinct().count();
        this.clusterType = nodes.get(0).allocation().get().membership().cluster().type();
        this.fulfilment = 1;
    }

    public AllocatableClusterResources(ClusterResources realResources,
                                       NodeResources advertisedResources,
                                       NodeResources idealResources,
                                       ClusterSpec.Type clusterType) {
        this.realResources = realResources.nodeResources();
        this.advertisedResources = advertisedResources;
        this.nodes = realResources.nodes();
        this.groups = realResources.groups();
        this.clusterType = clusterType;
        this.fulfilment = fulfilment(realResources.nodeResources(), idealResources);
    }

    public AllocatableClusterResources(ClusterResources realResources,
                                       Flavor flavor,
                                       NodeResources idealResources,
                                       ClusterSpec.Type clusterType,
                                       HostResourcesCalculator calculator) {
        this.realResources = realResources.nodeResources();
        this.advertisedResources = calculator.advertisedResourcesOf(flavor);
        this.nodes = realResources.nodes();
        this.groups = realResources.groups();
        this.clusterType = clusterType;
        this.fulfilment = fulfilment(realResources.nodeResources(), idealResources);
    }

    /**
     * Returns the resources which will actually be available per node in this cluster with this allocation.
     * These should be used for reasoning about allocation to meet measured demand.
     */
    public NodeResources realResources() { return realResources; }

    /**
     * Returns the resources advertised by the cloud provider, which are the basis for charging
     * and which must be used in resource allocation requests
     */
    public NodeResources advertisedResources() { return advertisedResources; }

    public ClusterResources toAdvertisedClusterResources() {
        return new ClusterResources(nodes, groups, advertisedResources);
    }

    public int nodes() { return nodes; }
    public int groups() { return groups; }

    public int groupSize() {
        // ceil: If the division does not produce a whole number we assume some node is missing
        return (int)Math.ceil((double)nodes / groups);
    }

    public ClusterSpec.Type clusterType() { return clusterType; }

    public double cost() { return nodes * advertisedResources.cost(); }

    /**
     * Returns the fraction measuring how well the real resources fulfils the ideal: 1 means completely fulfilled,
     * 0 means we have zero real resources.
     * The real may be short of the ideal due to resource limits imposed by the system or application.
     */
    public double fulfilment() { return fulfilment; }

    private static double fulfilment(NodeResources realResources, NodeResources idealResources) {
        double vcpuFulfilment     = Math.min(1, realResources.vcpu()     / idealResources.vcpu());
        double memoryGbFulfilment = Math.min(1, realResources.memoryGb() / idealResources.memoryGb());
        double diskGbFulfilment   = Math.min(1, realResources.diskGb()   / idealResources.diskGb());
        return (vcpuFulfilment + memoryGbFulfilment + diskGbFulfilment) / 3;
    }

    public boolean preferableTo(AllocatableClusterResources other) {
        if (this.fulfilment > other.fulfilment) return true; // we always want to fulfil as much as possible
        return this.cost() < other.cost(); // otherwise, prefer lower cost
    }

    @Override
    public String toString() {
        return nodes + " nodes with " + realResources() +
               " at cost $" + cost() +
               (fulfilment < 1.0 ? " (fulfilment " + fulfilment + ")" : "");
    }

    /**
     * Returns the best matching allocatable node resources given ideal node resources,
     * or empty if none available within the limits.
     */
    public static Optional<AllocatableClusterResources> from(ClusterResources resources,
                                                             ClusterSpec.Type clusterType,
                                                             Limits limits,
                                                             NodeRepository nodeRepository) {
        NodeResources cappedNodeResources = limits.cap(resources.nodeResources());
        cappedNodeResources = new NodeResourceLimits(nodeRepository.zone()).enlargeToLegal(cappedNodeResources, clusterType);

        if (nodeRepository.zone().getCloud().allowHostSharing()) {
            // return the requested resources, or empty if they cannot fit on existing hosts
            for (Flavor flavor : nodeRepository.flavors().getFlavors()) {
                if (flavor.resources().satisfies(cappedNodeResources))
                    return Optional.of(new AllocatableClusterResources(resources.with(cappedNodeResources),
                                                                       cappedNodeResources,
                                                                       resources.nodeResources(),
                                                                       clusterType));
            }
            return Optional.empty();
        }
        else {
            // return the cheapest flavor satisfying the target resources, if any
            Optional<AllocatableClusterResources> best = Optional.empty();
            for (Flavor flavor : nodeRepository.flavors().getFlavors()) {
                NodeResources advertisedResources = nodeRepository.resourcesCalculator().advertisedResourcesOf(flavor);
                NodeResources realResources = flavor.resources();

                // Adjust where we don't need exact match to the flavor
                if (flavor.resources().storageType() == NodeResources.StorageType.remote) {
                    advertisedResources = advertisedResources.withDiskGb(cappedNodeResources.diskGb());
                    realResources = realResources.withDiskGb(cappedNodeResources.diskGb());
                }
                if (flavor.resources().bandwidthGbps() >= cappedNodeResources.bandwidthGbps()) {
                    advertisedResources = advertisedResources.withBandwidthGbps(cappedNodeResources.bandwidthGbps());
                    realResources = realResources.withBandwidthGbps(cappedNodeResources.bandwidthGbps());
                }

                if ( ! between(limits.min().nodeResources(), limits.max().nodeResources(), advertisedResources)) continue;

                var candidate = new AllocatableClusterResources(resources.with(realResources),
                                                                advertisedResources,
                                                                resources.nodeResources(),
                                                                clusterType);
                if (best.isEmpty() || candidate.preferableTo(best.get()))
                    best = Optional.of(candidate);
            }
            return best;
        }
    }

    private static boolean between(NodeResources min, NodeResources max, NodeResources r) {
        if ( ! min.isUnspecified() && ! r.justNumbers().satisfies(min.justNumbers())) return false;
        if ( ! max.isUnspecified() && ! max.justNumbers().satisfies(r.justNumbers())) return false;
        return true;
    }

}
