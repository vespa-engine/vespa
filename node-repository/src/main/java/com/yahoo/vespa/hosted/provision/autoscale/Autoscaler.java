// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.host.FlavorOverrides;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.NodeResourceLimits;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The autoscaler makes decisions about the flavor and node count that should be allocated to a cluster
 * based on observed behavior.
 *
 * @author bratseth
 */
public class Autoscaler {

    /*
     TODO:
     - Scale group size
     - Consider taking spikes/variance into account
     - Measure observed regulation lag (startup+redistribution) and take it into account when deciding regulation observation window
     - Test AutoscalingMaintainer
     - Scale by performance not just load+cost
     */

    private static final int minimumMeasurements = 500; // TODO: Per node instead? Also say something about interval?

    /** What cost difference factor is worth a reallocation? */
    private static final double costDifferenceWorthReallocation = 0.1;
    /** What difference factor for a resource is worth a reallocation? */
    private static final double resourceDifferenceWorthReallocation = 0.1;

    private final HostResourcesCalculator resourcesCalculator;
    private final NodeMetricsDb metricsDb;
    private final NodeRepository nodeRepository;
    private final NodeResourceLimits nodeResourceLimits;

    public Autoscaler(HostResourcesCalculator resourcesCalculator,
                      NodeMetricsDb metricsDb,
                      NodeRepository nodeRepository) {
        this.resourcesCalculator = resourcesCalculator;
        this.metricsDb = metricsDb;
        this.nodeRepository = nodeRepository;
        this.nodeResourceLimits = new NodeResourceLimits(nodeRepository.zone());
    }

    /**
     * Autoscale a cluster
     *
     * @param clusterNodes the list of all the active nodes in a cluster
     * @return a new suggested allocation for this cluster, or empty if it should not be rescaled at this time
     */
    public Optional<AllocatableClusterResources> autoscale(Cluster cluster, List<Node> clusterNodes) {
        if (clusterNodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                                   node.allocation().get().membership().retired() ||
                                                   node.allocation().get().isRemovable())) {
            return Optional.empty(); // Don't autoscale clusters that are in flux
        }

        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();
        AllocatableClusterResources currentAllocation = new AllocatableClusterResources(clusterNodes, resourcesCalculator);
        Optional<Double> cpuLoad    = averageLoad(Resource.cpu, clusterNodes, clusterType);
        Optional<Double> memoryLoad = averageLoad(Resource.memory, clusterNodes, clusterType);
        Optional<Double> diskLoad   = averageLoad(Resource.disk, clusterNodes, clusterType);
        if (cpuLoad.isEmpty() || memoryLoad.isEmpty() || diskLoad.isEmpty()) return Optional.empty();

        Optional<AllocatableClusterResources> bestAllocation = findBestAllocation(cpuLoad.get(),
                                                                                  memoryLoad.get(),
                                                                                  diskLoad.get(),
                                                                                  currentAllocation,
                                                                                  cluster);
        if (bestAllocation.isEmpty()) return Optional.empty();
        if (similar(bestAllocation.get(), currentAllocation)) return Optional.empty();
        return bestAllocation;
    }

    private Optional<AllocatableClusterResources> findBestAllocation(double cpuLoad, double memoryLoad, double diskLoad,
                                                                     AllocatableClusterResources currentAllocation,
                                                                     Cluster cluster) {
        Optional<AllocatableClusterResources> bestAllocation = Optional.empty();
        for (ResourceIterator i = new ResourceIterator(cpuLoad, memoryLoad, diskLoad, currentAllocation, cluster); i.hasNext(); ) {
            Optional<AllocatableClusterResources> allocatableResources = toAllocatableResources(i.next(),
                                                                                                currentAllocation.clusterType(),
                                                                                                cluster);
            if (allocatableResources.isEmpty()) continue;
            if (bestAllocation.isEmpty() || allocatableResources.get().preferableTo(bestAllocation.get()))
                bestAllocation = allocatableResources;
        }
        return bestAllocation;
    }

    /** Returns true if both total real resources and total cost are similar */
    private boolean similar(AllocatableClusterResources a, AllocatableClusterResources b) {
        return similar(a.cost(), b.cost(), costDifferenceWorthReallocation) &&
               similar(a.realResources().vcpu() * a.nodes(),
                       b.realResources().vcpu() * b.nodes(), resourceDifferenceWorthReallocation) &&
               similar(a.realResources().memoryGb() * a.nodes(),
                       b.realResources().memoryGb() * b.nodes(), resourceDifferenceWorthReallocation) &&
               similar(a.realResources().diskGb() * a.nodes(),
                       b.realResources().diskGb() * b.nodes(),
                       resourceDifferenceWorthReallocation);
    }

    private boolean similar(double r1, double r2, double threshold) {
        return Math.abs(r1 - r2) / r1 < threshold;
    }

    /**
     * Returns the smallest allocatable node resources larger than the given node resources,
     * or empty if none available.
     */
    private Optional<AllocatableClusterResources> toAllocatableResources(ClusterResources resources,
                                                                         ClusterSpec.Type clusterType,
                                                                         Cluster cluster) {
        NodeResources nodeResources = resources.nodeResources();
        if ( ! cluster.minResources().equals(cluster.maxResources())) // enforce application limits unless suggest mode
            nodeResources = cluster.capAtLimits(nodeResources);
        nodeResources = nodeResourceLimits.enlargeToLegal(nodeResources, clusterType); // enforce system limits

        if (allowsHostSharing(nodeRepository.zone().cloud())) {
            // return the requested resources, or empty if they cannot fit on existing hosts
            for (Flavor flavor : nodeRepository.getAvailableFlavors().getFlavors()) {
                if (flavor.resources().satisfies(nodeResources))
                    return Optional.of(new AllocatableClusterResources(resources.with(nodeResources),
                                                                       nodeResources,
                                                                       resources.nodeResources(),
                                                                       clusterType));
            }
            return Optional.empty();
        }
        else {
            // return the cheapest flavor satisfying the target resources, if any
            Optional<AllocatableClusterResources> best = Optional.empty();
            for (Flavor flavor : nodeRepository.getAvailableFlavors().getFlavors()) {
                if ( ! flavor.resources().satisfies(nodeResources)) continue;

                if (flavor.resources().storageType() == NodeResources.StorageType.remote)
                    flavor = flavor.with(FlavorOverrides.ofDisk(nodeResources.diskGb()));
                var candidate = new AllocatableClusterResources(resources.with(flavor.resources()),
                                                                flavor,
                                                                resources.nodeResources(),
                                                                clusterType,
                                                                resourcesCalculator);

                if (best.isEmpty() || candidate.cost() <= best.get().cost())
                    best = Optional.of(candidate);
            }
            return best;
        }
    }

    /**
     * Returns the average load of this resource in the measurement window,
     * or empty if we are not in a position to make decisions from these measurements at this time.
     */
    private Optional<Double> averageLoad(Resource resource, List<Node> clusterNodes, ClusterSpec.Type clusterType) {
        NodeMetricsDb.Window window = metricsDb.getWindow(nodeRepository.clock().instant().minus(scalingWindow(clusterType)),
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

}
