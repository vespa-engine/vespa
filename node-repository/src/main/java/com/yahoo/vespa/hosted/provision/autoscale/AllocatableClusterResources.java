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
    public AllocatableClusterResources(ClusterResources requested,
                                       ClusterSpec.Type clusterType,
                                       NodeRepository nodeRepository) {
        this.nodes = requested.nodes();
        this.groups = requested.groups();
        this.realResources = nodeRepository.resourcesCalculator().requestToReal(requested.nodeResources());
        this.advertisedResources = requested.nodeResources();
        this.clusterType = clusterType;
        this.fulfilment = 1;
    }

    public AllocatableClusterResources(List<Node> nodes, NodeRepository nodeRepository) {
        this.nodes = nodes.size();
        this.groups = (int)nodes.stream().map(node -> node.allocation().get().membership().cluster().group()).distinct().count();
        this.realResources = averageRealResourcesOf(nodes, nodeRepository); // Average since we average metrics over nodes
        this.advertisedResources = nodes.get(0).flavor().resources();
        this.clusterType = nodes.get(0).allocation().get().membership().cluster().type();
        this.fulfilment = 1;
    }

    public AllocatableClusterResources(ClusterResources realResources,
                                       NodeResources advertisedResources,
                                       NodeResources idealResources,
                                       ClusterSpec.Type clusterType) {
        this.nodes = realResources.nodes();
        this.groups = realResources.groups();
        this.realResources = realResources.nodeResources();
        this.advertisedResources = advertisedResources;
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
        if (this.fulfilment < 1 || other.fulfilment < 1)
            return this.fulfilment > other.fulfilment;  // we always want to fulfil as much as possible
        return this.cost() < other.cost(); // otherwise, prefer lower cost
    }

    @Override
    public String toString() {
        return nodes + " nodes " +
               ( groups > 1 ? "(in " + groups + " groups) " : "" ) +
               "with " + advertisedResources() +
               " at cost $" + cost() +
               (fulfilment < 1.0 ? " (fulfilment " + fulfilment + ")" : "");
    }

    private static NodeResources averageRealResourcesOf(List<Node> nodes, NodeRepository nodeRepository) {
        NodeResources sum = new NodeResources(0, 0, 0, 0);
        for (Node node : nodes)
            sum = sum.add(nodeRepository.resourcesCalculator().realResourcesOf(node, nodeRepository).justNumbers());
        return nodes.get(0).flavor().resources().justNonNumbers()
                                                .withVcpu(sum.vcpu() / nodes.size())
                                                .withMemoryGb(sum.memoryGb() / nodes.size())
                                                .withDiskGb(sum.diskGb() / nodes.size())
                                                .withBandwidthGbps(sum.bandwidthGbps() / nodes.size());
    }

    /**
     * Returns the best matching allocatable node resources given ideal node resources,
     * or empty if none available within the limits.
     *
     * @param resources the real resources that should ideally be allocated
     * @param exclusive whether resources should be allocated on entire hosts
     *        (in which case the allocated resources will be all the real resources of the host
     *         and limits are required to encompass the full resources of candidate host flavors)
     */
    public static Optional<AllocatableClusterResources> from(ClusterResources resources,
                                                             boolean exclusive,
                                                             ClusterSpec.Type clusterType,
                                                             Limits limits,
                                                             NodeRepository nodeRepository) {
        NodeResources cappedNodeResources = limits.cap(resources.nodeResources());
        cappedNodeResources = new NodeResourceLimits(nodeRepository).enlargeToLegal(cappedNodeResources, clusterType);

        if ( !exclusive && nodeRepository.zone().getCloud().allowHostSharing()) { // Check if any flavor can fit these hosts
            // We decide resources: Add overhead to what we'll request (advertised) to make sure real becomes (at least) cappedNodeResources
            NodeResources realResources = cappedNodeResources;
            NodeResources advertisedResources = nodeRepository.resourcesCalculator().realToRequest(realResources);
            for (Flavor flavor : nodeRepository.flavors().getFlavors()) {
                if (flavor.resources().satisfies(advertisedResources))
                    return Optional.of(new AllocatableClusterResources(resources.with(realResources),
                                                                       advertisedResources,
                                                                       resources.nodeResources(),
                                                                       clusterType));
            }
            return Optional.empty();
        }
        else { // Return the cheapest flavor satisfying the requested resources, if any
            Optional<AllocatableClusterResources> best = Optional.empty();
            for (Flavor flavor : nodeRepository.flavors().getFlavors()) {
                // Flavor decide resources: Real resources are the worst case real resources we'll get if we ask for these advertised resources
                NodeResources advertisedResources = nodeRepository.resourcesCalculator().advertisedResourcesOf(flavor);
                NodeResources realResources = nodeRepository.resourcesCalculator().requestToReal(advertisedResources);

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
        if ( ! min.isUnspecified() && ! min.justNonNumbers().compatibleWith(r.justNonNumbers())) return false;
        if ( ! max.isUnspecified() && ! max.justNonNumbers().compatibleWith(r.justNonNumbers())) return false;
        if ( ! min.isUnspecified() && ! r.justNumbers().satisfies(min.justNumbers())) return false;
        if ( ! max.isUnspecified() && ! max.justNumbers().satisfies(r.justNumbers())) return false;
        return true;
    }

}
