// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
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

    private final ClusterSpec clusterSpec;

    private final double fulfilment;

    /** Fake allocatable resources from requested capacity */
    public AllocatableClusterResources(ClusterResources requested,
                                       ClusterSpec clusterSpec,
                                       NodeRepository nodeRepository) {
        this.nodes = requested.nodes();
        this.groups = requested.groups();
        this.realResources = nodeRepository.resourcesCalculator().requestToReal(requested.nodeResources(), clusterSpec.isExclusive());
        this.advertisedResources = requested.nodeResources();
        this.clusterSpec = clusterSpec;
        this.fulfilment = 1;
    }

    public AllocatableClusterResources(List<Node> nodes, NodeRepository nodeRepository, boolean exclusive) {
        this.nodes = nodes.size();
        this.groups = (int)nodes.stream().map(node -> node.allocation().get().membership().cluster().group()).distinct().count();
        this.realResources = averageRealResourcesOf(nodes, nodeRepository, exclusive); // Average since we average metrics over nodes
        this.advertisedResources = nodes.get(0).allocation().get().requestedResources();
        this.clusterSpec = nodes.get(0).allocation().get().membership().cluster();
        this.fulfilment = 1;
    }

    public AllocatableClusterResources(ClusterResources realResources,
                                       NodeResources advertisedResources,
                                       NodeResources idealResources,
                                       ClusterSpec clusterSpec) {
        this.nodes = realResources.nodes();
        this.groups = realResources.groups();
        this.realResources = realResources.nodeResources();
        this.advertisedResources = advertisedResources;
        this.clusterSpec = clusterSpec;
        this.fulfilment = fulfilment(realResources.nodeResources(), idealResources);
    }

    /**
     * Returns the resources which will actually be available per node in this cluster with this allocation.
     * These should be used for reasoning about allocation to meet measured demand.
     */
    public ClusterResources realResources() {
        return new ClusterResources(nodes, groups, realResources);
    }

    /**
     * Returns the resources advertised by the cloud provider, which are the basis for charging
     * and which must be used in resource allocation requests
     */
    public ClusterResources advertisedResources() {
        return new ClusterResources(nodes, groups, advertisedResources);
    }

    public int nodes() { return nodes; }
    public int groups() { return groups; }

    public int groupSize() {
        // ceil: If the division does not produce a whole number we assume some node is missing
        return (int)Math.ceil((double)nodes / groups);
    }

    public ClusterSpec clusterSpec() { return clusterSpec; }

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
        return advertisedResources() +
               " at cost $" + cost() +
               (fulfilment < 1.0 ? " (fulfilment " + fulfilment + ")" : "");
    }

    private static NodeResources averageRealResourcesOf(List<Node> nodes, NodeRepository nodeRepository, boolean exclusive) {
        NodeResources sum = new NodeResources(0, 0, 0, 0);
        for (Node node : nodes)
            sum = sum.add(nodeRepository.resourcesCalculator().realResourcesOf(node, nodeRepository, exclusive).justNumbers());
        return nodes.get(0).allocation().get().requestedResources().justNonNumbers()
                                       .withVcpu(sum.vcpu() / nodes.size())
                                       .withMemoryGb(sum.memoryGb() / nodes.size())
                                       .withDiskGb(sum.diskGb() / nodes.size())
                                       .withBandwidthGbps(sum.bandwidthGbps() / nodes.size());
    }

    public static Optional<AllocatableClusterResources> from(ClusterResources wantedResources,
                                                             ClusterSpec clusterSpec,
                                                             Limits applicationLimits,
                                                             NodeList hosts,
                                                             NodeRepository nodeRepository) {
        var systemLimits = new NodeResourceLimits(nodeRepository);
        boolean exclusive = clusterSpec.isExclusive();
        if ( !clusterSpec.isExclusive() && !nodeRepository.zone().getCloud().dynamicProvisioning()) {
            // We decide resources: Add overhead to what we'll request (advertised) to make sure real becomes (at least) cappedNodeResources
            var advertisedResources = nodeRepository.resourcesCalculator().realToRequest(wantedResources.nodeResources(), exclusive);
            advertisedResources = systemLimits.enlargeToLegal(advertisedResources, clusterSpec.type(), exclusive); // Ask for something legal
            advertisedResources = applicationLimits.cap(advertisedResources); // Overrides other conditions, even if it will then fail
            var realResources = nodeRepository.resourcesCalculator().requestToReal(advertisedResources, exclusive); // What we'll really get
            if ( ! systemLimits.isWithinRealLimits(realResources, clusterSpec.type())) return Optional.empty();
            if (matchesAny(hosts, advertisedResources))
                    return Optional.of(new AllocatableClusterResources(wantedResources.with(realResources),
                                                                       advertisedResources,
                                                                       wantedResources.nodeResources(),
                                                                       clusterSpec));
            else
                return Optional.empty();
        }
        else { // Return the cheapest flavor satisfying the requested resources, if any
            NodeResources cappedWantedResources = applicationLimits.cap(wantedResources.nodeResources());
            Optional<AllocatableClusterResources> best = Optional.empty();
            for (Flavor flavor : nodeRepository.flavors().getFlavors()) {
                // Flavor decide resources: Real resources are the worst case real resources we'll get if we ask for these advertised resources
                NodeResources advertisedResources = nodeRepository.resourcesCalculator().advertisedResourcesOf(flavor);
                NodeResources realResources = nodeRepository.resourcesCalculator().requestToReal(advertisedResources, exclusive);

                // Adjust where we don't need exact match to the flavor
                if (flavor.resources().storageType() == NodeResources.StorageType.remote) {
                    advertisedResources = advertisedResources.withDiskGb(cappedWantedResources.diskGb());
                    realResources = realResources.withDiskGb(cappedWantedResources.diskGb());
                }
                if (flavor.resources().bandwidthGbps() >= advertisedResources.bandwidthGbps()) {
                    advertisedResources = advertisedResources.withBandwidthGbps(cappedWantedResources.bandwidthGbps());
                    realResources = realResources.withBandwidthGbps(cappedWantedResources.bandwidthGbps());
                }

                if ( ! between(applicationLimits.min().nodeResources(), applicationLimits.max().nodeResources(), advertisedResources)) continue;
                if ( ! systemLimits.isWithinRealLimits(realResources, clusterSpec.type())) continue;
                var candidate = new AllocatableClusterResources(wantedResources.with(realResources),
                                                                advertisedResources,
                                                                wantedResources.nodeResources(),
                                                                clusterSpec);
                if (best.isEmpty() || candidate.preferableTo(best.get()))
                    best = Optional.of(candidate);
            }
            return best;
        }
    }

    /** Returns true if the given resources could be allocated on any of the given host flavors */
    private static boolean matchesAny(NodeList hosts, NodeResources advertisedResources) {
        // Tenant nodes should not consume more than half the resources of the biggest hosts
        // to make it easier to shift them between hosts.
        return hosts.stream().anyMatch(host -> host.resources().withVcpu(host.resources().vcpu() / 2)
                                                   .satisfies(advertisedResources));
    }

    private static boolean between(NodeResources min, NodeResources max, NodeResources r) {
        if ( ! min.isUnspecified() && ! min.justNonNumbers().compatibleWith(r.justNonNumbers())) return false;
        if ( ! max.isUnspecified() && ! max.justNonNumbers().compatibleWith(r.justNonNumbers())) return false;
        if ( ! min.isUnspecified() && ! r.justNumbers().satisfies(min.justNumbers())) return false;
        if ( ! max.isUnspecified() && ! max.justNumbers().satisfies(r.justNumbers())) return false;
        return true;
    }

}
