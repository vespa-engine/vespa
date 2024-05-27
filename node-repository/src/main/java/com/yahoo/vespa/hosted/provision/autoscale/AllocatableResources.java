// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * @author bratseth
 */
public class AllocatableResources {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(AllocatableResources.class.getName());
    /** The node count in the cluster */
    private final int nodes;

    /** The number of node groups in the cluster */
    private final int groups;

    private final NodeResources realResources;
    private final NodeResources advertisedResources;

    private final ClusterSpec clusterSpec;

    private final double fulfilment;

    /** Fake allocatable resources from requested capacity */
    public AllocatableResources(ClusterResources requested,
                                ClusterSpec clusterSpec,
                                NodeRepository nodeRepository,
                                CloudAccount cloudAccount) {
        this.nodes = requested.nodes();
        this.groups = requested.groups();
        this.realResources = nodeRepository.resourcesCalculator().requestToReal(requested.nodeResources(), cloudAccount,
                                                                                nodeRepository.exclusivity().allocation(clusterSpec), false);
        this.advertisedResources = requested.nodeResources();
        this.clusterSpec = clusterSpec;
        this.fulfilment = 1;
    }

    public AllocatableResources(NodeList nodes, NodeRepository nodeRepository) {
        this.nodes = nodes.size();
        this.groups = (int)nodes.stream().map(node -> node.allocation().get().membership().cluster().group()).distinct().count();
        this.realResources = averageRealResourcesOf(nodes.asList(), nodeRepository); // Average since we average metrics over nodes
        this.advertisedResources = nodes.requestedResources();
        this.clusterSpec = nodes.clusterSpec();
        this.fulfilment = 1;
    }

    public AllocatableResources(ClusterResources realResources,
                                NodeResources advertisedResources,
                                ClusterResources idealResources,
                                ClusterSpec clusterSpec) {
        this.nodes = realResources.nodes();
        this.groups = realResources.groups();
        this.realResources = realResources.nodeResources();
        this.advertisedResources = advertisedResources;
        this.clusterSpec = clusterSpec;
        this.fulfilment = fulfilment(realResources, idealResources);
    }

    private AllocatableResources(int nodes,
                                 int groups,
                                 NodeResources realResources,
                                 NodeResources advertisedResources,
                                 ClusterSpec clusterSpec,
                                 double fulfilment) {
        this.nodes = nodes;
        this.groups = groups;
        this.realResources = realResources;
        this.advertisedResources = advertisedResources;
        this.clusterSpec = clusterSpec;
        this.fulfilment = fulfilment;
    }

    /** Returns this with the redundant node or group removed from counts. */
    public AllocatableResources withoutRedundancy() {
        int groupSize = nodes / groups;
        int nodesAdjustedForRedundancy   = nodes > 1 ? (groups == 1 ? nodes - 1 : nodes - groupSize) : nodes;
        int groupsAdjustedForRedundancy  = nodes > 1 ? (groups == 1 ? 1 : groups - 1) : groups;
        return new AllocatableResources(nodesAdjustedForRedundancy,
                                        groupsAdjustedForRedundancy,
                                        realResources,
                                        advertisedResources,
                                        clusterSpec,
                                        fulfilment);
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

    public ClusterSpec clusterSpec() { return clusterSpec; }

    /** Returns the standard cost of these resources, in dollars per hour */
    public double cost() { return nodes * advertisedResources.cost(); }

    /**
     * Returns the fraction measuring how well the real resources fulfils the ideal: 1 means completely fulfilled,
     * 0 means we have zero real resources.
     * The real may be short of the ideal due to resource limits imposed by the system or application.
     */
    public double fulfilment() { return fulfilment; }

    private static double fulfilment(ClusterResources realResources, ClusterResources idealResources) {
        double vcpuFulfilment     = Math.min(1, realResources.totalResources().vcpu()     / idealResources.totalResources().vcpu());
        double memoryGbFulfilment = Math.min(1, realResources.totalResources().memoryGb() / idealResources.totalResources().memoryGb());
        double diskGbFulfilment   = Math.min(1, realResources.totalResources().diskGb()   / idealResources.totalResources().diskGb());
        return (vcpuFulfilment + memoryGbFulfilment + diskGbFulfilment) / 3;
    }

    public boolean preferableTo(AllocatableResources other, ClusterModel model) {
        if (other.fulfilment() < 1 || this.fulfilment() < 1) // always fulfil as much as possible
            return this.fulfilment() > other.fulfilment();

        return this.cost() * toHours(model.allocationDuration()) + this.costChangingFrom(model)
               <
               other.cost() * toHours(model.allocationDuration()) + other.costChangingFrom(model);
    }

    private double toHours(Duration duration) {
        return duration.toMillis() / 3600000.0;
    }

    /** The estimated cost of changing from the given current resources to this. */
    public double costChangingFrom(ClusterModel model) {
        return new ResourceChange(model, this).cost();
    }

    @Override
    public String toString() {
        return advertisedResources() +
               " at cost $" + cost() +
               (fulfilment < 1.0 ? " (fulfilment " + fulfilment + ")" : "");
    }

    private static NodeResources averageRealResourcesOf(List<Node> nodes, NodeRepository nodeRepository) {
        NodeResources sum = new NodeResources(0, 0, 0, 0).justNumbers();
        for (Node node : nodes) {
            sum = sum.add(nodeRepository.resourcesCalculator().realResourcesOf(node, nodeRepository).justNumbers());
        }
        return nodes.get(0).allocation().get().requestedResources()
                                       .withVcpu(sum.vcpu() / nodes.size())
                                       .withMemoryGb(sum.memoryGb() / nodes.size())
                                       .withDiskGb(sum.diskGb() / nodes.size())
                                       .withBandwidthGbps(sum.bandwidthGbps() / nodes.size());
    }

    public static Optional<AllocatableResources> from(ClusterResources wantedResources,
                                                      ApplicationId applicationId,
                                                      ClusterSpec clusterSpec,
                                                      Limits applicationLimits,
                                                      List<NodeResources> availableRealHostResources,
                                                      ClusterModel model,
                                                      NodeRepository nodeRepository, boolean enableDetailedLogging) {
        var systemLimits = nodeRepository.nodeResourceLimits();
        boolean exclusive = nodeRepository.exclusivity().allocation(clusterSpec);
        if (! exclusive) {
            // We decide resources: Add overhead to what we'll request (advertised) to make sure real becomes (at least) cappedNodeResources
            var allocatableResources = calculateAllocatableResources(wantedResources,
                                                                     nodeRepository,
                                                                     model.cloudAccount(),
                                                                     clusterSpec,
                                                                     applicationLimits,
                                                                     exclusive,
                                                                     true);

            var worstCaseRealResources = nodeRepository.resourcesCalculator().requestToReal(allocatableResources.advertisedResources,
                                                                                            model.cloudAccount(),
                                                                                            exclusive,
                                                                                            false);
            if ( ! systemLimits.isWithinRealLimits(worstCaseRealResources, clusterSpec)) {
                allocatableResources = calculateAllocatableResources(wantedResources,
                                                                     nodeRepository,
                                                                     model.cloudAccount(),
                                                                     clusterSpec,
                                                                     applicationLimits,
                                                                     exclusive,
                                                                     false);
            }

            if ( ! systemLimits.isWithinRealLimits(allocatableResources.realResources, clusterSpec))
                return Optional.empty();
            if ( ! anySatisfies(allocatableResources.realResources, availableRealHostResources))
                return Optional.empty();
            return Optional.of(allocatableResources);
        }
        else { // Return the cheapest flavor satisfying the requested resources, if any
            NodeResources cappedWantedResources = applicationLimits.cap(wantedResources.nodeResources());
            Optional<AllocatableResources> best = Optional.empty();
            Optional<AllocatableResources> bestDisregardingDiskLimit = Optional.empty();
            for (Flavor flavor : nodeRepository.flavors().getFlavors()) {
                // Flavor decide resources: Real resources are the worst case real resources we'll get if we ask for these advertised resources
                NodeResources advertisedResources = nodeRepository.resourcesCalculator().advertisedResourcesOf(flavor);
                NodeResources realResources = nodeRepository.resourcesCalculator().requestToReal(advertisedResources, model.cloudAccount(), exclusive, false);

                // Adjust where we don't need exact match to the flavor
                if (flavor.resources().storageType() == NodeResources.StorageType.remote) {
                    double diskGb = systemLimits.enlargeToLegal(cappedWantedResources, clusterSpec, exclusive, true).diskGb();
                    if (diskGb > applicationLimits.max().nodeResources().diskGb() || diskGb < applicationLimits.min().nodeResources().diskGb()) // TODO: Remove when disk limit is enforced
                        diskGb = systemLimits.enlargeToLegal(cappedWantedResources, clusterSpec, exclusive, false).diskGb();
                    advertisedResources = advertisedResources.withDiskGb(diskGb);
                    realResources = realResources.withDiskGb(diskGb);
                }
                if (flavor.resources().bandwidthGbps() >= advertisedResources.bandwidthGbps()) {
                    advertisedResources = advertisedResources.withBandwidthGbps(cappedWantedResources.bandwidthGbps());
                    realResources = realResources.withBandwidthGbps(cappedWantedResources.bandwidthGbps());
                }

                if ( ! between(applicationLimits.min().nodeResources(), applicationLimits.max().nodeResources(), advertisedResources)) continue;
                if ( ! systemLimits.isWithinRealLimits(realResources, clusterSpec)) continue;

                var candidate = new AllocatableResources(wantedResources.with(realResources),
                                                         advertisedResources,
                                                         wantedResources,
                                                         clusterSpec);
                if (enableDetailedLogging) {
                    log.fine("AllocatableResources with: " +
                            "\n\t Real Resources: " + wantedResources.with(realResources).toString() +
                             "\n\t Advertised Resources: " + advertisedResources +
                             "\n\t Wanted Resources: " + wantedResources);
                }

                if ( ! systemLimits.isWithinAdvertisedDiskLimits(advertisedResources, clusterSpec)) { // TODO: Remove when disk limit is enforced
                    if (bestDisregardingDiskLimit.isEmpty() || candidate.preferableTo(bestDisregardingDiskLimit.get(), model)) {
                        bestDisregardingDiskLimit = Optional.of(candidate);
                    }
                    continue;
                }
                if (best.isEmpty() || candidate.preferableTo(best.get(), model)) {
                    best = Optional.of(candidate);
                }
            }
            if (best.isEmpty())
                best = bestDisregardingDiskLimit;
            return best;
        }
    }

    private static AllocatableResources calculateAllocatableResources(ClusterResources wantedResources,
                                                                      NodeRepository nodeRepository,
                                                                      CloudAccount cloudAccount,
                                                                      ClusterSpec clusterSpec,
                                                                      Limits applicationLimits,
                                                                      boolean exclusive,
                                                                      boolean bestCase) {
        var systemLimits = nodeRepository.nodeResourceLimits();
        var advertisedResources = nodeRepository.resourcesCalculator().realToRequest(wantedResources.nodeResources(), cloudAccount, exclusive, bestCase);
        advertisedResources = systemLimits.enlargeToLegal(advertisedResources, clusterSpec, exclusive, true); // Ask for something legal
        advertisedResources = applicationLimits.cap(advertisedResources); // Overrides other conditions, even if it will then fail
        var realResources = nodeRepository.resourcesCalculator().requestToReal(advertisedResources, cloudAccount, exclusive, bestCase); // What we'll really get
        if ( ! systemLimits.isWithinRealLimits(realResources, clusterSpec)
             && advertisedResources.storageType() == NodeResources.StorageType.any) {
            // Since local disk reserves some of the storage, try to constrain to remote disk
            advertisedResources = advertisedResources.with(NodeResources.StorageType.remote);
            realResources = nodeRepository.resourcesCalculator().requestToReal(advertisedResources, cloudAccount, exclusive, bestCase);
        }
        return new AllocatableResources(wantedResources.with(realResources),
                                        advertisedResources,
                                        wantedResources,
                                        clusterSpec);
    }

    /** Returns true if the given resources could be allocated on any of the given host flavors */
    private static boolean anySatisfies(NodeResources realResources, List<NodeResources> availableRealHostResources) {
        return availableRealHostResources.stream().anyMatch(realHostResources -> realHostResources.satisfies(realResources));
    }

    private static boolean between(NodeResources min, NodeResources max, NodeResources r) {
        if ( ! min.isUnspecified() && ! min.justNonNumbers().compatibleWith(r.justNonNumbers())) return false;
        if ( ! max.isUnspecified() && ! max.justNonNumbers().compatibleWith(r.justNonNumbers())) return false;
        if ( ! min.isUnspecified() && ! r.justNumbers().satisfies(min.justNumbers())) return false;
        if ( ! max.isUnspecified() && ! max.justNumbers().satisfies(r.justNumbers())) return false;
        return true;
    }

}
