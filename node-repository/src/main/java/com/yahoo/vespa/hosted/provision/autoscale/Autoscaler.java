// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private final NodeMetricsDb metricsDb;
    private final NodeRepository nodeRepository;
    private final AllocationOptimizer allocationOptimizer;

    public Autoscaler(NodeMetricsDb metricsDb, NodeRepository nodeRepository) {
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
        return autoscale(clusterNodes, Limits.empty(), cluster.exclusive())
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
        return autoscale(clusterNodes, Limits.of(cluster), cluster.exclusive())
                       .map(AllocatableClusterResources::toAdvertisedClusterResources);
    }

    private Optional<AllocatableClusterResources> autoscale(List<Node> clusterNodes, Limits limits, boolean exclusive) {
        if (unstable(clusterNodes)) return Optional.empty();

        AllocatableClusterResources currentAllocation = new AllocatableClusterResources(clusterNodes, nodeRepository);
        Optional<Double> cpuLoad    = averageLoad(Resource.cpu, clusterNodes);
        Optional<Double> memoryLoad = averageLoad(Resource.memory, clusterNodes);
        Optional<Double> diskLoad   = averageLoad(Resource.disk, clusterNodes);
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

    /**
     * Returns the average load of this resource in the measurement window,
     * or empty if we are not in a position to make decisions from these measurements at this time.
     */
    private Optional<Double> averageLoad(Resource resource,
                                         List<Node> clusterNodes) {
        ApplicationId application = clusterNodes.get(0).allocation().get().owner();
        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();

        Instant startTime = nodeRepository.clock().instant().minus(scalingWindow(clusterType));

        Optional<Long> generation = Optional.empty();
        List<NodeMetricsDb.AutoscalingEvent> deployments = metricsDb.getEvents(application);
        Map<String, Instant> startTimePerHost = new HashMap<>();
        if (! deployments.isEmpty()) {
            var deployment = deployments.get(deployments.size() - 1);
            if (deployment.time().isAfter(startTime))
                startTime = deployment.time(); // just to filter more faster
            List<NodeMetricsDb.NodeMeasurements> generationMeasurements = metricsDb.getMeasurements(startTime,
                                                                                                    Metric.generation,
                                                                                                    clusterNodes.stream().map(Node::hostname).collect(Collectors.toList()));
            for (Node node : clusterNodes) {
                startTimePerHost.put(node.hostname(), nodeRepository.clock().instant()); // Discard all unless we can prove otherwise
                var nodeGenerationMeasurements =
                        generationMeasurements.stream().filter(m -> m.hostname().equals(node.hostname())).findAny();
                if (nodeGenerationMeasurements.isPresent()) {
                    var firstMeasurementOfCorrectGeneration = nodeGenerationMeasurements.get().asList().stream().filter(m -> m.value() < deployment.generation()).findFirst();
                    if (firstMeasurementOfCorrectGeneration.isPresent())
                        startTimePerHost.put(node.hostname(), firstMeasurementOfCorrectGeneration.get().at());
                }
            }
        }

        List<NodeMetricsDb.NodeMeasurements> measurements = metricsDb.getMeasurements(startTime,
                                                                                      Metric.from(resource),
                                                                                      clusterNodes.stream().map(Node::hostname).collect(Collectors.toList()));
        measurements = filterStale(measurements, startTimePerHost);
        // Require a total number of measurements scaling with the number of nodes,
        // but don't require that we have at least that many from every node
        int measurementCount = measurements.stream().mapToInt(m -> m.size()).sum();
        if (measurementCount / clusterNodes.size() < minimumMeasurementsPerNode(clusterType)) return Optional.empty();
        if (measurements.size() != clusterNodes.size()) return Optional.empty();

        double measurementSum = measurements.stream().flatMap(m -> m.asList().stream()).mapToDouble(m -> m.value()).sum();
        return Optional.of(measurementSum / measurementCount);
    }

    private List<NodeMetricsDb.NodeMeasurements> filterStale(List<NodeMetricsDb.NodeMeasurements> measurements,
                                                             Map<String, Instant> startTimePerHost) {
        if (startTimePerHost.isEmpty()) return measurements; // Map is either empty or complete
        return measurements.stream().map(m -> m.copyAfter(startTimePerHost.get(m.hostname()))).collect(Collectors.toList());
    }

    /** The duration of the window we need to consider to make a scaling decision. See also minimumMeasurementsPerNode */
    static Duration scalingWindow(ClusterSpec.Type clusterType) {
        if (clusterType.isContent()) return Duration.ofHours(12);
        return Duration.ofHours(1);
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
