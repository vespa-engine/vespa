// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The autoscaler makes decisions about the flavor and node count that should be allocated to a cluster
 * based on observed behavior.
 *
 * @author bratseth
 */
public class Autoscaler {

    /*
     TODO:
     - X Don't always go for more, smaller nodes
     - X Test gc
     - X Test AutoscalingMaintainer
     - X Implement node metrics fetch
     - X Avoid making decisions for the same app at multiple config servers
     - Have a better idea about whether we have sufficient information to make decisions
     - Consider taking spikes/variance into account
     - Measure observed regulation lag (startup+redistribution) into account when deciding regulation observation window
     */

    private static final int minimumMeasurements = 500; // TODO: Per node instead? Also say something about interval?

    /** Only change if the difference between the current and best ratio is larger than this */
    private static final double resourceDifferenceRatioWorthReallocation = 0.1;

    // We only depend on the ratios between these values
    private static final double cpuUnitCost = 12.0;
    private static final double memoryUnitCost = 1.2;
    private static final double diskUnitCost = 0.045;

    private final HostResourcesCalculator hostResourcesCalculator;
    private final NodeMetricsDb metricsDb;
    private final NodeRepository nodeRepository;

    public Autoscaler(HostResourcesCalculator hostResourcesCalculator,
                      NodeMetricsDb metricsDb,
                      NodeRepository nodeRepository) {
        this.hostResourcesCalculator = hostResourcesCalculator;
        this.metricsDb = metricsDb;
        this.nodeRepository = nodeRepository;
    }

    public Optional<ClusterResources> autoscale(ApplicationId applicationId, ClusterSpec cluster, List<Node> clusterNodes) {
        if (clusterNodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                                   node.allocation().get().membership().retired() ||
                                                   node.allocation().get().isRemovable()))
            return Optional.empty(); // Don't autoscale clusters that are in flux
        ClusterResources currentAllocation = new ClusterResources(clusterNodes);
        Optional<Double> totalCpuSpent    = averageUseOf(Resource.cpu,    applicationId, cluster, clusterNodes);
        Optional<Double> totalMemorySpent = averageUseOf(Resource.memory, applicationId, cluster, clusterNodes);
        Optional<Double> totalDiskSpent   = averageUseOf(Resource.disk,   applicationId, cluster, clusterNodes);
        if (totalCpuSpent.isEmpty() || totalMemorySpent.isEmpty() || totalDiskSpent.isEmpty()) return Optional.empty();

        System.out.println("  Total cpu " + totalCpuSpent.get() + " total memory " + totalMemorySpent.get() + " total disk " + totalDiskSpent.get());
        Optional<ClusterResources> bestAllocation = findBestAllocation(totalCpuSpent.get(),
                                                                       totalMemorySpent.get(),
                                                                       totalDiskSpent.get(),
                                                                       currentAllocation);
        System.out.println("  Best allocation: " + bestAllocation);
        if (bestAllocation.isPresent() && isSimilar(bestAllocation.get(), currentAllocation))
            return Optional.empty(); // Avoid small changes
        return bestAllocation;
    }

    private Optional<ClusterResources> findBestAllocation(double totalCpu, double totalMemory, double totalDisk,
                                                          ClusterResources currentAllocation) {
        Optional<ClusterResourcesWithCost> bestAllocation = Optional.empty();
        for (ResourceIterator i = new ResourceIterator(totalCpu, totalMemory, totalDisk, currentAllocation); i.hasNext(); ) {
            ClusterResources allocation = i.next();
            System.out.println("    Considering " + allocation.nodes() + " nodes:");
            Optional<ClusterResourcesWithCost> allocatableResources = toAllocatableResources(allocation);
            if (allocatableResources.isEmpty()) continue;
            System.out.println("    -- Candidate: " + allocatableResources);
            if (bestAllocation.isEmpty() || allocatableResources.get().cost() < bestAllocation.get().cost())
                bestAllocation = allocatableResources;
        }
        return bestAllocation.map(a -> a.clusterResources());
    }

    private boolean isSimilar(ClusterResources a1, ClusterResources a2) {
        if (a1.nodes() != a2.nodes()) return false; // A full node is always a significant difference
        return isSimilar(a1.nodeResources().vcpu(), a2.nodeResources().vcpu()) &&
               isSimilar(a1.nodeResources().memoryGb(), a2.nodeResources().memoryGb()) &&
               isSimilar(a1.nodeResources().diskGb(), a2.nodeResources().diskGb());
    }

    private boolean isSimilar(double r1, double r2) {
        return Math.abs(r1 - r2) / r1 < resourceDifferenceRatioWorthReallocation;
    }

    /**
     * Returns the smallest allocatable node resources larger than the given node resources,
     * or empty if none available.
     */
    private Optional<ClusterResourcesWithCost> toAllocatableResources(ClusterResources resources) {
        if (allowsHostSharing(nodeRepository.zone().cloud())) {
            // Return the requested resources, or empty if they cannot fit on existing hosts
            for (Flavor flavor : nodeRepository.getAvailableFlavors().getFlavors())
                if (flavor.resources().satisfies(resources.nodeResources()))
                    return Optional.of(new ClusterResourcesWithCost(resources,
                                                                    costOf(resources.nodeResources()) * resources.nodes()));
            return Optional.empty();
        }
        else {
            // return the cheapest flavor satisfying the target resources, if any
            double bestCost = Double.MAX_VALUE;
            Optional<Flavor> bestFlavor = Optional.empty();
            for (Flavor flavor : nodeRepository.getAvailableFlavors().getFlavors()) {
                if ( ! flavor.resources().satisfies(resources.nodeResources())) {
                    System.out.println("      " + flavor.name() + " does not satisfy " + resources.nodeResources());
                    continue;
                }
                if (bestFlavor.isEmpty() || bestCost > costOf(flavor.resources())) {
                    bestFlavor = Optional.of(flavor);
                    bestCost = costOf(flavor);
                }
            }
            if (bestFlavor.isEmpty())
                return Optional.empty();
            else
                return Optional.of(new ClusterResourcesWithCost(resources.with(bestFlavor.get().resources()),
                                                                bestCost * resources.nodes()));
        }
    }

    /**
     * Returns the average total (over all nodes) of this resource in the measurement window,
     * or empty if we are not in a position to take decisions from these measurements at this time.
     */
    private Optional<Double> averageUseOf(Resource resource, ApplicationId applicationId, ClusterSpec cluster, List<Node> clusterNodes) {
        NodeResources currentResources = clusterNodes.get(0).flavor().resources();

        NodeMetricsDb.Window window = metricsDb.getWindow(nodeRepository.clock().instant().minus(scalingWindow(cluster.type())),
                                                          resource,
                                                          clusterNodes);

        if (window.measurementCount() < minimumMeasurements) return Optional.empty();
        if (window.hostnames() != clusterNodes.size()) return Optional.empty(); // Regulate only when all nodes are measured

        return Optional.of(window.average() * resource.valueFrom(currentResources) * clusterNodes.size());
    }

    /** The duration of the window we need to consider to make a scaling decision */
    private Duration scalingWindow(ClusterSpec.Type clusterType) {
        if (clusterType.isContent()) return Duration.ofHours(12); // Ideally we should use observed redistribution time
        return Duration.ofHours(12); // TODO: Measure much more often to get this down to minutes. And, ideally we should take node startup time into account
    }

    // TODO: Put this in zone config instead?
    private boolean allowsHostSharing(CloudName cloudName) {
        if (cloudName.value().equals("aws")) return false;
        return true;
    }

    private double costOf(Flavor flavor) {
        NodeResources chargedResources = hostResourcesCalculator.availableCapacityOf(flavor.name(), flavor.resources());
        return costOf(chargedResources);
    }

    static double costOf(NodeResources resources) {
        return resources.vcpu() * cpuUnitCost +
               resources.memoryGb() * memoryUnitCost +
               resources.diskGb() * diskUnitCost;
    }

}
