// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

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

    /** What cost difference factor is worth a reallocation? */
    private static final double costDifferenceWorthReallocation = 0.1;
    /** What difference factor for a resource is worth a reallocation? */
    private static final double resourceDifferenceWorthReallocation = 0.1;

    private final MetricsDb metricsDb;
    private final NodeRepository nodeRepository;
    private final AllocationOptimizer allocationOptimizer;

    public Autoscaler(MetricsDb metricsDb, NodeRepository nodeRepository) {
        this.metricsDb = metricsDb;
        this.nodeRepository = nodeRepository;
        this.allocationOptimizer = new AllocationOptimizer(nodeRepository);
    }

    /**
     * Suggest a scaling of a cluster. This returns a better allocation (if found)
     * without taking min and max limits into account.
     *
     * @param clusterNodes the list of all the active nodes in a cluster
     * @return a new suggested allocation for this cluster, or empty if it should not be rescaled at this time
     */
    public Optional<ClusterResources> suggest(Cluster cluster, List<Node> clusterNodes) {
        return autoscale(cluster, clusterNodes, Limits.empty(), cluster.exclusive())
                       .map(AllocatableClusterResources::toAdvertisedClusterResources);

    }

    /**
     * Autoscale a cluster by load. This returns a better allocation (if found) inside the min and max limits.
     *
     * @param clusterNodes the list of all the active nodes in a cluster
     * @return a new suggested allocation for this cluster, or empty if it should not be rescaled at this time
     */
    public Optional<ClusterResources> autoscale(Cluster cluster, List<Node> clusterNodes) {
        if (cluster.minResources().equals(cluster.maxResources())) return Optional.empty(); // Shortcut
        return autoscale(cluster, clusterNodes, Limits.of(cluster), cluster.exclusive())
                       .map(AllocatableClusterResources::toAdvertisedClusterResources);
    }

    private Optional<AllocatableClusterResources> autoscale(Cluster cluster,
                                                            List<Node> clusterNodes, Limits limits, boolean exclusive) {
        if (unstable(clusterNodes)) return Optional.empty();

        AllocatableClusterResources currentAllocation = new AllocatableClusterResources(clusterNodes, nodeRepository);

        ClusterTimeseries clusterTimeseries = new ClusterTimeseries(cluster, clusterNodes, metricsDb, nodeRepository);

        Optional<Double> cpuLoad    = clusterTimeseries.averageLoad(Resource.cpu);
        Optional<Double> memoryLoad = clusterTimeseries.averageLoad(Resource.memory);
        Optional<Double> diskLoad   = clusterTimeseries.averageLoad(Resource.disk);
        if (cpuLoad.isEmpty() || memoryLoad.isEmpty() || diskLoad.isEmpty()) return Optional.empty();
        var target = ResourceTarget.idealLoad(cpuLoad.get(), memoryLoad.get(), diskLoad.get(), currentAllocation);

        Optional<AllocatableClusterResources> bestAllocation =
                allocationOptimizer.findBestAllocation(target, currentAllocation, limits, exclusive);
        if (bestAllocation.isEmpty()) return Optional.empty();
        if (similar(bestAllocation.get(), currentAllocation)) return Optional.empty();
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
                       b.realResources().diskGb() * b.nodes(), resourceDifferenceWorthReallocation);
    }

    private boolean similar(double r1, double r2, double threshold) {
        return Math.abs(r1 - r2) / (( r1 + r2) / 2) < threshold;
    }

    /** The duration of the window we need to consider to make a scaling decision. See also minimumMeasurementsPerNode */
    static Duration scalingWindow(ClusterSpec.Type clusterType) {
        if (clusterType.isContent()) return Duration.ofHours(12);
        return Duration.ofHours(1);
    }

    static Duration maxScalingWindow() {
        return Duration.ofHours(12);
    }

    /** Measurements are currently taken once a minute. See also scalingWindow */
    static int minimumMeasurementsPerNode(ClusterSpec.Type clusterType) {
        if (clusterType.isContent()) return 60;
        return 20;
    }

    public static boolean unstable(List<Node> nodes) {
        return nodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                               node.allocation().get().membership().retired() ||
                                               node.allocation().get().isRemovable());
    }

}
