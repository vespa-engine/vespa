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
import java.util.stream.Collectors;

/**
 * A series of metric snapshots for all nodes in a cluster
 *
 * @author bratseth
 */
public class ClusterTimeseries {

    private final List<Node> clusterNodes;

    /** The measurements for all hosts in this snapshot */
    private final List<NodeTimeseries> nodeTimeseries;

    public ClusterTimeseries(Cluster cluster, List<Node> clusterNodes, MetricsDb db, NodeRepository nodeRepository) {
        this.clusterNodes = clusterNodes;
        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();
        var allTimeseries = db.getNodeTimeseries(nodeRepository.clock().instant().minus(Autoscaler.scalingWindow(clusterType)),
                                                 clusterNodes.stream().map(Node::hostname).collect(Collectors.toSet()));
        Map<String, Instant> startTimePerNode = metricStartTimes(cluster, clusterNodes, allTimeseries, nodeRepository);
        nodeTimeseries = filterStale(allTimeseries, startTimePerNode);
    }

    /**
     * Returns the instant of the oldest metric to consider for each node, or an empty map if metrics from the
     * entire (max) window should be considered.
     */
    private Map<String, Instant> metricStartTimes(Cluster cluster,
                                                  List<Node> clusterNodes,
                                                  List<NodeTimeseries> nodeTimeseries,
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

    /** Returns the average number of measurements per node */
    public int measurementsPerNode() {
        int measurementCount = nodeTimeseries.stream().mapToInt(m -> m.size()).sum();
        return measurementCount / clusterNodes.size();
    }

    /** Returns the number of nodes measured in this */
    public int nodesMeasured() {
        return nodeTimeseries.size();
    }

    /** Returns the average load of this resource in this */
    public double averageLoad(Resource resource) {
        int measurementCount = nodeTimeseries.stream().mapToInt(m -> m.size()).sum();
        double measurementSum = nodeTimeseries.stream().flatMap(m -> m.asList().stream()).mapToDouble(m -> value(resource, m)).sum();
        return measurementSum / measurementCount;
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
