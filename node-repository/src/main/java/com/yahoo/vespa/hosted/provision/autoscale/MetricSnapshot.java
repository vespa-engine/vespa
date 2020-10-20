// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.metrics.simple.Measurement;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A snapshot which implements the questions we want to ask about metrics for one cluster at one point in time.
 *
 * @author bratseth
 */
public class MetricSnapshot {

    private final List<Node> clusterNodes;
    private final Map<String, Instant> startTimePerHost;

    /** The measurements for all hosts in this snapshot */
    private final List<NodeMetricsDb.NodeMeasurements> measurements;

    public MetricSnapshot(Cluster cluster, List<Node> clusterNodes, NodeMetricsDb db, NodeRepository nodeRepository) {
        this.clusterNodes = clusterNodes;
        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();
        this.measurements = db.getMeasurements(nodeRepository.clock().instant().minus(Autoscaler.scalingWindow(clusterType)),
                                               clusterNodes.stream().map(Node::hostname).collect(Collectors.toList()));
        this.startTimePerHost = metricStartTimes(cluster, clusterNodes, nodeRepository);
    }

    /**
     * Returns the instant of the oldest metric to consider for each node, or an empty map if metrics from the
     * entire (max) window should be considered.
     */
    private Map<String, Instant> metricStartTimes(Cluster cluster,
                                                  List<Node> clusterNodes,
                                                  NodeRepository nodeRepository) {
        Map<String, Instant> startTimePerHost = new HashMap<>();
        if ( ! cluster.scalingEvents().isEmpty()) {
            var deployment = cluster.scalingEvents().get(cluster.scalingEvents().size() - 1);
            for (Node node : clusterNodes) {
                startTimePerHost.put(node.hostname(), nodeRepository.clock().instant()); // Discard all unless we can prove otherwise
                var nodeGenerationMeasurements =
                        measurements.stream().filter(m -> m.hostname().equals(node.hostname())).findAny();
                if (nodeGenerationMeasurements.isPresent()) {
                    var firstMeasurementOfCorrectGeneration =
                            nodeGenerationMeasurements.get().asList().stream()
                                                      .filter(m -> m.generation() >= deployment.generation())
                                                      .findFirst();
                    if (firstMeasurementOfCorrectGeneration.isPresent()) {
                        startTimePerHost.put(node.hostname(), firstMeasurementOfCorrectGeneration.get().at());
                    }
                }
            }
        }
        return startTimePerHost;
    }

    /**
     * Returns the average load of this resource in the measurement window,
     * or empty if we are not in a position to make decisions from these measurements at this time.
     */
    public Optional<Double> averageLoad(Resource resource) {
        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();

        List<NodeMetricsDb.NodeMeasurements> currentMeasurements = filterStale(measurements, startTimePerHost);

        // Require a total number of measurements scaling with the number of nodes,
        // but don't require that we have at least that many from every node
        int measurementCount = currentMeasurements.stream().mapToInt(m -> m.size()).sum();
        if (measurementCount / clusterNodes.size() < Autoscaler.minimumMeasurementsPerNode(clusterType)) return Optional.empty();
        if (currentMeasurements.size() != clusterNodes.size()) return Optional.empty();

        double measurementSum = currentMeasurements.stream().flatMap(m -> m.asList().stream()).mapToDouble(m -> value(resource, m)).sum();
        return Optional.of(measurementSum / measurementCount);
    }

    private double value(Resource resource, NodeMetricsDb.Measurements measurement) {
        switch (resource) {
            case cpu: return measurement.cpu();
            case memory: return measurement.memory();
            case disk: return measurement.disk();
            default: throw new IllegalArgumentException("Got an unknown resource " + resource);
        }
    }

    private List<NodeMetricsDb.NodeMeasurements> filterStale(List<NodeMetricsDb.NodeMeasurements> measurements,
                                                             Map<String, Instant> startTimePerHost) {
        if (startTimePerHost.isEmpty()) return measurements; // Map is either empty or complete
        return measurements.stream().map(m -> m.copyAfter(startTimePerHost.get(m.hostname()))).collect(Collectors.toList());
    }

}
