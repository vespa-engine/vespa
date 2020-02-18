// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

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

    private static final int minimumMeasurements = 500; // TODO: Per node instead? Also say something about interval?

    // We only depend on the ratios between these values
    private static final double cpuUnitCost = 12.0;
    private static final double memoryUnitCost = 1.2;
    private static final double diskUnitCost = 0.045;

    // Configured min and max nodes TODO: These should come from the application package
    private int minimumNodesPerCluster = 3;
    private int maximumNodesPerCluster = 10;

    private final NodeMetricsDb metricsDb;
    private final NodeRepository nodeRepository;

    public Autoscaler(NodeMetricsDb metricsDb, NodeRepository nodeRepository) {
        this.metricsDb = metricsDb;
        this.nodeRepository = nodeRepository;
    }

    public Optional<ClusterResources> autoscale(ApplicationId applicationId, ClusterSpec cluster, List<Node> clusterNodes) {
        Optional<Double> totalCpuSpent    = averageUseOf(Resource.cpu,    applicationId, cluster, clusterNodes);
        Optional<Double> totalMemorySpent = averageUseOf(Resource.memory, applicationId, cluster, clusterNodes);
        Optional<Double> totalDiskSpent   = averageUseOf(Resource.disk,   applicationId, cluster, clusterNodes);
        if (totalCpuSpent.isEmpty() || totalMemorySpent.isEmpty() || totalDiskSpent.isEmpty()) return Optional.empty();

        Optional<ClusterResources> bestTarget = Optional.empty();
        // Try all the node counts allowed by the configuration -
        // -1 to translate from true allocated counts to counts allowing for a node to be down
        for (int targetCount = minimumNodesPerCluster - 1; targetCount <= maximumNodesPerCluster - 1; targetCount++ ) {
            // The resources per node we need if we distribute the total spent over targetCount nodes at ideal load:
            NodeResources targetResources = targetResources(targetCount,
                                                            totalCpuSpent.get(), totalMemorySpent.get(), totalDiskSpent.get(),
                                                            clusterNodes.get(0).flavor().resources());
            Optional<ClusterResources> target = toEffectiveResources(targetCount, targetResources);
            System.out.println("Trying " + targetCount + " nodes: " + targetResources + ", effective: " + target);
            if (target.isEmpty()) continue;

            if (bestTarget.isEmpty() || target.get().cost() < bestTarget.get().cost())
                bestTarget = target;
        }
        return bestTarget;
    }

    /**
     * Returns the practical (allocatable and with redundancy) resources corresponding to the given target resources,
     * or empty if this target is illegal
     */
    private Optional<ClusterResources> toEffectiveResources(int targetCount, NodeResources targetResources) {
        Optional<NodeResources> effectiveResources = toEffectiveResources(targetResources);
        if (effectiveResources.isEmpty()) return Optional.empty();

        int effectiveCount = targetCount + 1; // need one extra node for redundancy

        return Optional.of(new ClusterResources(effectiveCount, effectiveResources.get()));
    }

    /**
     * Returns the smallest allocatable node resources larger than the given node resources,
     * or empty if none available.
     */
    private Optional<NodeResources> toEffectiveResources(NodeResources nodeResources) {
        if (allowsHostSharing(nodeRepository.zone().cloud())) {
            // Return the requested resources, or empty if they cannot fit on existing hosts
            for (Flavor flavor : nodeRepository.getAvailableFlavors().getFlavors()) {
                if (flavor.resources().satisfies(nodeResources)) return Optional.of(nodeResources);
            }
            return Optional.empty();
        }
        else {
            // return the cheapest flavor satisfying the target resources, if any
            double bestCost = Double.MAX_VALUE;
            Optional<Flavor> bestFlavor = Optional.empty();
            for (Flavor flavor : nodeRepository.getAvailableFlavors().getFlavors()) {
                // TODO: Use effective not advertised flavor resources
                if ( ! flavor.resources().satisfies(nodeResources)) continue;
                if (bestFlavor.isEmpty() || bestCost > costOf(flavor.resources())) {
                    bestFlavor = Optional.of(flavor);
                    bestCost = costOf(flavor.resources());
                }
            }
            return bestFlavor.map(flavor -> flavor.resources());
        }
    }

    /** Returns the resources needed per node to be at ideal load given a target node count and total resource allocation */
    private NodeResources targetResources(int nodeCount,
                                          double totalCpu, double totalMemory, double totalDisk,
                                          NodeResources currentResources) {

        return currentResources.withVcpu(totalCpu / nodeCount / Resource.cpu.idealAverageLoad())
                               .withMemoryGb(totalMemory / nodeCount / Resource.memory.idealAverageLoad())
                               .withDiskGb(totalDisk / nodeCount / Resource.disk.idealAverageLoad());
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
        // TODO: Bail also if allocations have changed in the time window

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

    static double costOf(NodeResources resources) {
        return resources.vcpu() * cpuUnitCost +
               resources.memoryGb() * memoryUnitCost +
               resources.diskGb() * diskUnitCost;
    }

}
