// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.host.FlavorOverrides;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.NodeResourceLimits;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The autoscaler makes decisions about the flavor and node count that should be allocated to a cluster
 * based on observed behavior.
 *
 * @author bratseth
 */
public class Autoscaler {

    private Logger log = Logger.getLogger(Autoscaler.class.getName());

    /*
     TODO:
     - Scale group size
     - Have a better idea about whether we have sufficient information to make decisions
     - Consider taking spikes/variance into account
     - Measure observed regulation lag (startup+redistribution) into account when deciding regulation observation window
     - Test AutoscalingMaintainer
     - Scale by performance not just load+cost
     */

    private static final int minimumMeasurements = 500; // TODO: Per node instead? Also say something about interval?

    /** What cost difference factor warrants reallocation? */
    private static final double costDifferenceRatioWorthReallocation = 0.1;
    /** What difference factor from ideal (for any resource) warrants a change? */
    private static final double idealDivergenceWorthReallocation = 0.1;

    // We only depend on the ratios between these values
    private static final double cpuUnitCost = 12.0;
    private static final double memoryUnitCost = 1.2;
    private static final double diskUnitCost = 0.045;

    private final HostResourcesCalculator hostResourcesCalculator;
    private final NodeMetricsDb metricsDb;
    private final NodeRepository nodeRepository;
    private final NodeResourceLimits nodeResourceLimits;

    public Autoscaler(HostResourcesCalculator hostResourcesCalculator,
                      NodeMetricsDb metricsDb,
                      NodeRepository nodeRepository) {
        this.hostResourcesCalculator = hostResourcesCalculator;
        this.metricsDb = metricsDb;
        this.nodeRepository = nodeRepository;
        this.nodeResourceLimits = new NodeResourceLimits(nodeRepository.zone());
    }

    public Optional<ClusterResources> autoscale(ApplicationId applicationId, ClusterSpec cluster, List<Node> clusterNodes) {
        if (clusterNodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                                   node.allocation().get().membership().retired() ||
                                                   node.allocation().get().isRemovable()))
            return Optional.empty(); // Don't autoscale clusters that are in flux
        ClusterResources currentAllocation = new ClusterResources(clusterNodes);
        Optional<Double> cpuLoad    = averageLoad(Resource.cpu, cluster, clusterNodes);
        Optional<Double> memoryLoad = averageLoad(Resource.memory, cluster, clusterNodes);
        Optional<Double> diskLoad   = averageLoad(Resource.disk, cluster, clusterNodes);
        if (cpuLoad.isEmpty() || memoryLoad.isEmpty() || diskLoad.isEmpty()) {
            log.fine("Autoscaling " + applicationId + " " + cluster + ": Insufficient metrics to decide");
            return Optional.empty();
        }

        Optional<ClusterResourcesWithCost> bestAllocation = findBestAllocation(cpuLoad.get(),
                                                                               memoryLoad.get(),
                                                                               diskLoad.get(),
                                                                               currentAllocation,
                                                                               cluster);
        if (bestAllocation.isEmpty()) {
            log.fine("Autoscaling " + applicationId + " " + cluster + ": Could not find a better allocation");
            return Optional.empty();
        }

        if (closeToIdeal(Resource.cpu, cpuLoad.get()) &&
            closeToIdeal(Resource.memory, memoryLoad.get()) &&
            closeToIdeal(Resource.disk, diskLoad.get()) &&
            similarCost(bestAllocation.get().cost(), currentAllocation.nodes() * costOf(currentAllocation.nodeResources()))) {
            log.fine("Autoscaling " + applicationId + " " + cluster + ": Resources are almost ideal and price difference is small");
            return Optional.empty(); // Avoid small, unnecessary changes
        }
        return bestAllocation.map(a -> a.clusterResources());
    }

    private Optional<ClusterResourcesWithCost> findBestAllocation(double cpuLoad, double memoryLoad, double diskLoad,
                                                                  ClusterResources currentAllocation, ClusterSpec cluster) {
        Optional<ClusterResourcesWithCost> bestAllocation = Optional.empty();
        for (ResourceIterator i = new ResourceIterator(cpuLoad, memoryLoad, diskLoad, currentAllocation); i.hasNext(); ) {
            ClusterResources allocation = i.next();
            Optional<ClusterResourcesWithCost> allocatableResources = toAllocatableResources(allocation, cluster);
            if (allocatableResources.isEmpty()) continue;
            if (bestAllocation.isEmpty() || allocatableResources.get().cost() < bestAllocation.get().cost())
                bestAllocation = allocatableResources;
        }
        return bestAllocation;
    }

    private boolean similarCost(double cost1, double cost2) {
        return similar(cost1, cost2, costDifferenceRatioWorthReallocation);
    }

    private boolean closeToIdeal(Resource resource, double value) {
        return similar(resource.idealAverageLoad(), value, idealDivergenceWorthReallocation);
    }

    private boolean similar(double r1, double r2, double threshold) {
        return Math.abs(r1 - r2) / r1 < threshold;
    }

    /**
     * Returns the smallest allocatable node resources larger than the given node resources,
     * or empty if none available.
     */
    private Optional<ClusterResourcesWithCost> toAllocatableResources(ClusterResources resources, ClusterSpec cluster) {
        if (allowsHostSharing(nodeRepository.zone().cloud())) {
            // Return the requested resources, adjusted to be legal or empty if they cannot fit on existing hosts
            NodeResources nodeResources = nodeResourceLimits.enlargeToLegal(resources.nodeResources(), cluster.type());
            for (Flavor flavor : nodeRepository.getAvailableFlavors().getFlavors())
                if (flavor.resources().satisfies(nodeResources))
                    return Optional.of(new ClusterResourcesWithCost(resources.with(nodeResources),
                                                                    costOf(nodeResources) * resources.nodes()));
            return Optional.empty();
        }
        else {
            // return the cheapest flavor satisfying the target resources, if any
            double bestCost = Double.MAX_VALUE;
            Optional<Flavor> bestFlavor = Optional.empty();
            for (Flavor flavor : nodeRepository.getAvailableFlavors().getFlavors()) {
                if ( ! flavor.resources().satisfies(resources.nodeResources())) continue;
                if (flavor.resources().storageType() == NodeResources.StorageType.remote)
                    flavor = flavor.with(FlavorOverrides.ofDisk(resources.nodeResources().diskGb()));
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
     * Returns the average load of this resource in the measurement window,
     * or empty if we are not in a position to make decisions from these measurements at this time.
     */
    private Optional<Double> averageLoad(Resource resource, ClusterSpec cluster, List<Node> clusterNodes) {
        NodeMetricsDb.Window window = metricsDb.getWindow(nodeRepository.clock().instant().minus(scalingWindow(cluster.type())),
                                                          resource,
                                                          clusterNodes.stream().map(Node::hostname).collect(Collectors.toList()));

        if (window.measurementCount() < minimumMeasurements) return Optional.empty();
        if (window.hostnames() != clusterNodes.size()) return Optional.empty(); // Regulate only when all nodes are measured

        return Optional.of(window.average());
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

    private double costOf(NodeResources resources) {
        return resources.vcpu() * cpuUnitCost +
               resources.memoryGb() * memoryUnitCost +
               resources.diskGb() * diskUnitCost;
    }

}
