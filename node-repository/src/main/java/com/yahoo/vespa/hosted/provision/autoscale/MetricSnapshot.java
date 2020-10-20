// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
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
    private final NodeMetricsDb db;
    private final NodeRepository nodeRepository;
    private final Map<String, Instant> startTimePerHost;

    public MetricSnapshot(Cluster cluster, List<Node> clusterNodes, NodeMetricsDb db, NodeRepository nodeRepository) {
        this.clusterNodes = clusterNodes;
        this.db = db;
        this.nodeRepository = nodeRepository;
        this.startTimePerHost = metricStartTimes(cluster, clusterNodes, db, nodeRepository);
        startTimePerHost.forEach((a,b) -> System.out.println(a + " = " + b));
    }

    /**
     * Returns the instant of the oldest metric to consider for each node, or an empty map if metrics from the
     * entire (max) window should be considered.
     */
    private static Map<String, Instant> metricStartTimes(Cluster cluster,
                                                         List<Node> clusterNodes,
                                                         NodeMetricsDb db,
                                                         NodeRepository nodeRepository) {
        Map<String, Instant> startTimePerHost = new HashMap<>();
        if ( ! cluster.scalingEvents().isEmpty()) {
            var deployment = cluster.scalingEvents().get(cluster.scalingEvents().size() - 1);
            List<NodeMetricsDb.NodeMeasurements> generationMeasurements =
                    db.getMeasurements(deployment.at(),
                                       Metric.generation,
                                       clusterNodes.stream().map(Node::hostname).collect(Collectors.toList()));
            for (Node node : clusterNodes) {
                startTimePerHost.put(node.hostname(), nodeRepository.clock().instant()); // Discard all unless we can prove otherwise
                var nodeGenerationMeasurements =
                        generationMeasurements.stream().filter(m -> m.hostname().equals(node.hostname())).findAny();
                if (nodeGenerationMeasurements.isPresent()) {
                    var firstMeasurementOfCorrectGeneration =
                            nodeGenerationMeasurements.get().asList().stream()
                                                      .filter(m -> m.value() >= deployment.generation())
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

        List<NodeMetricsDb.NodeMeasurements> measurements =
                db.getMeasurements(nodeRepository.clock().instant().minus(Autoscaler.scalingWindow(clusterType)),
                                          Metric.from(resource),
                                          clusterNodes.stream().map(Node::hostname).collect(Collectors.toList()));
        measurements = filterStale(measurements, startTimePerHost);

        // Require a total number of measurements scaling with the number of nodes,
        // but don't require that we have at least that many from every node
        int measurementCount = measurements.stream().mapToInt(m -> m.size()).sum();
        if (measurementCount / clusterNodes.size() < Autoscaler.minimumMeasurementsPerNode(clusterType)) return Optional.empty();
        if (measurements.size() != clusterNodes.size()) return Optional.empty();

        double measurementSum = measurements.stream().flatMap(m -> m.asList().stream()).mapToDouble(m -> m.value()).sum();
        return Optional.of(measurementSum / measurementCount);
    }

    private List<NodeMetricsDb.NodeMeasurements> filterStale(List<NodeMetricsDb.NodeMeasurements> measurements,
                                                             Map<String, Instant> startTimePerHost) {
        if (startTimePerHost.isEmpty()) return measurements; // Map is either empty or complete
        return measurements.stream().map(m -> m.copyAfter(startTimePerHost.get(m.hostname()))).collect(Collectors.toList());
    }

}
