// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consumes a response from the metrics/v2 API and populates the fields of this with the resulting values
 *
 * @author bratseth
 */
public class MetricsResponse {

    private final Collection<Pair<String, MetricSnapshot>> nodeMetrics = new ArrayList<>();

    public MetricsResponse(String response, NodeList applicationNodes, NodeRepository nodeRepository) {
        this(SlimeUtils.jsonToSlime(response), applicationNodes, nodeRepository);
    }

    public Collection<Pair<String, MetricSnapshot>> metrics() { return nodeMetrics; }

    private MetricsResponse(Slime response, NodeList applicationNodes, NodeRepository nodeRepository) {
        Inspector root = response.get();
        Inspector nodes = root.field("nodes");
        nodes.traverse((ArrayTraverser)(__, node) -> consumeNode(node, applicationNodes, nodeRepository));
    }

    private void consumeNode(Inspector node, NodeList applicationNodes, NodeRepository nodeRepository) {
        String hostname = node.field("hostname").asString();
        consumeNodeMetrics(hostname, node.field("node"), applicationNodes, nodeRepository);
        // consumeServiceMetrics(hostname, node.field("services"));
    }

    private void consumeNodeMetrics(String hostname, Inspector nodeData, NodeList applicationNodes, NodeRepository nodeRepository) {
        Optional<Node> node = applicationNodes.stream().filter(n -> n.hostname().equals(hostname)).findAny();
        if (node.isEmpty()) return; // Node is not part of this cluster any more
        long timestampSecond = nodeData.field("timestamp").asLong();
        Map<String, Double> values = consumeMetrics(nodeData.field("metrics"));
        nodeMetrics.add(new Pair<>(hostname, new MetricSnapshot(Instant.ofEpochMilli(timestampSecond * 1000),
                                                                Metric.cpu.from(values),
                                                                Metric.memory.from(values),
                                                                Metric.disk.from(values),
                                                                (long)Metric.generation.from(values),
                                                                Metric.inService.from(values) > 0,
                                                                 clusterIsStable(node.get(), applicationNodes, nodeRepository))));
    }

    private boolean clusterIsStable(Node node, NodeList applicationNodes, NodeRepository nodeRepository) {
        ClusterSpec cluster = node.allocation().get().membership().cluster();
        return Autoscaler.stable(applicationNodes.cluster(cluster.id()), nodeRepository);
    }

    private void consumeServiceMetrics(String hostname, Inspector node) {
        String name = node.field("name").asString();
        long timestamp = node.field("timestamp").asLong();
        Map<String, Double> values = consumeMetrics(node.field("metrics"));
    }

    private Map<String, Double> consumeMetrics(Inspector metrics) {
        Map<String, Double> values = new HashMap<>();
        metrics.traverse((ArrayTraverser) (__, item) -> consumeMetricsItem(item, values));
        return values;
    }

    private void consumeMetricsItem(Inspector item, Map<String, Double> values) {
        item.field("values").traverse((ObjectTraverser)(name, value) -> values.put(name, value.asDouble()));
    }

    /** The metrics this can read */
    private enum Metric {

        cpu { // a node resource
            public String metricResponseName() { return "cpu.util"; }
            double convertValue(double metricValue) { return (float)metricValue / 100; } // % to ratio
        },
        memory { // a node resource
            public String metricResponseName() { return "mem_total.util"; }
            double convertValue(double metricValue) { return (float)metricValue / 100; } // % to ratio
        },
        disk { // a node resource
            public String metricResponseName() { return "disk.util"; }
            double convertValue(double metricValue) { return (float)metricValue / 100; } // % to ratio
        },
        generation { // application config generation active on the node
            public String metricResponseName() { return "application_generation"; }
            double convertValue(double metricValue) { return (float)metricValue; } // Really a long
            double defaultValue() { return -1.0; }
        },
        inService {
            public String metricResponseName() { return "in_service"; }
            double convertValue(double metricValue) { return (float)metricValue; } // Really a boolean
            double defaultValue() { return 1.0; }
        };

        /** The name of this metric as emitted from its source */
        public abstract String metricResponseName();

        /** Convert from the emitted value of this metric to the value we want to use here */
        abstract double convertValue(double metricValue);

        double defaultValue() { return 0.0; }

        public double from(Map<String, Double> values) {
            return convertValue(values.getOrDefault(metricResponseName(), defaultValue()));
        }

    }

}
