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
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A series of metric snapshots for all nodes in a cluster
 *
 * @author bratseth
 */
public class ClusterTimeseries {

    private static final Logger log = Logger.getLogger(ClusterTimeseries.class.getName());

    private final List<Node> clusterNodes;
    private final Map<String, Instant> startTimePerNode;

    /** The measurements for all hosts in this snapshot */
    private final List<NodeTimeseries> nodeTimeseries;

    public ClusterTimeseries(Cluster cluster, List<Node> clusterNodes, MetricsDb db, NodeRepository nodeRepository) {
        this.clusterNodes = clusterNodes;
        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();
        this.nodeTimeseries = db.getNodeTimeseries(nodeRepository.clock().instant().minus(Autoscaler.scalingWindow(clusterType)),
                                                   clusterNodes.stream().map(Node::hostname).collect(Collectors.toSet()));
        this.startTimePerNode = metricStartTimes(cluster, clusterNodes, nodeRepository);
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
                        nodeTimeseries.stream().filter(m -> m.hostname().equals(node.hostname())).findAny();
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
     * or empty if we do not have a reliable measurement across the cluster nodes.
     */
    public Optional<Double> averageLoad(Resource resource, Cluster cluster) {
        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();

        List<NodeTimeseries> currentMeasurements = filterStale(nodeTimeseries, startTimePerNode);

        // Require a total number of measurements scaling with the number of nodes,
        // but don't require that we have at least that many from every node
        int measurementCount = currentMeasurements.stream().mapToInt(m -> m.size()).sum();
        if (measurementCount / clusterNodes.size() < Autoscaler.minimumMeasurementsPerNode(clusterType)) {
            log.fine(() -> "Too few measurements per node for " + cluster.toString() + ": measurementCount " + measurementCount +
                           " (" + nodeTimeseries.stream().mapToInt(m -> m.size()).sum() + " before filtering");
            return Optional.empty();
        }
        if (currentMeasurements.size() != clusterNodes.size()) {
            log.fine(() -> "Mssing measurements from some nodes for " + cluster.toString() + ": Has from " + currentMeasurements.size() +
                           "but need " + clusterNodes.size() + "(before filtering: " +  nodeTimeseries.size() + ")");
            return Optional.empty();
        }

        double measurementSum = currentMeasurements.stream().flatMap(m -> m.asList().stream()).mapToDouble(m -> value(resource, m)).sum();
        return Optional.of(measurementSum / measurementCount);
    }

    private double value(Resource resource, MetricSnapshot snapshot) {
        switch (resource) {
            case cpu: return snapshot.cpu();
            case memory: return snapshot.memory();
            case disk: return snapshot.disk();
            default: throw new IllegalArgumentException("Got an unknown resource " + resource);
        }
    }

    private List<NodeTimeseries> filterStale(List<NodeTimeseries> timeseries,
                                             Map<String, Instant> startTimePerHost) {
        if (startTimePerHost.isEmpty()) return timeseries; // Map is either empty or complete
        return timeseries.stream().map(m -> m.justAfter(startTimePerHost.get(m.hostname()))).collect(Collectors.toList());
    }

}
