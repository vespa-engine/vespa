// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.ListMap;
import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A response containing metrics for a collection of nodes.
 *
 * @author bratseth
 */
public class MetricsResponse {

    /** Node level metrics */
    private final Collection<Pair<String, NodeMetricSnapshot>> nodeMetrics;

    /**
     * Cluster level metrics.
     * Must be aggregated at fetch time to avoid issues with nodes and nodes joining/leaving the cluster over time.
     */
    private final Map<ClusterSpec.Id, ClusterMetricSnapshot> clusterMetrics = new HashMap<>();

    /** Creates this from a metrics/V2 response */
    public MetricsResponse(String response, NodeList applicationNodes) {
        this(SlimeUtils.jsonToSlime(response), applicationNodes);
    }

    public MetricsResponse(Collection<Pair<String, NodeMetricSnapshot>> metrics) {
        this.nodeMetrics = metrics;
    }

    private MetricsResponse(Slime response, NodeList applicationNodes) {
        nodeMetrics = new ArrayList<>();
        Inspector root = response.get();
        Inspector nodes = root.field("nodes");
        nodes.traverse((ArrayTraverser)(__, node) -> consumeNode(node, applicationNodes));
    }

    public Collection<Pair<String, NodeMetricSnapshot>> nodeMetrics() { return nodeMetrics; }

    public Map<ClusterSpec.Id, ClusterMetricSnapshot> clusterMetrics() { return clusterMetrics; }

    private void consumeNode(Inspector nodeObject, NodeList applicationNodes) {
        String hostname = nodeObject.field("hostname").asString();
        Optional<Node> node = applicationNodes.node(hostname);
        if (node.isEmpty()) return; // Node is not part of this cluster any longer

        ListMap<String, Double> nodeValues = new ListMap<>();
        Instant at = consumeNodeMetrics(nodeObject.field("node"), nodeValues);
        consumeServiceMetrics(nodeObject.field("services"), nodeValues);

        nodeMetrics.add(new Pair<>(hostname, new NodeMetricSnapshot(at,
                                                                    new Load(Metric.cpu.from(nodeValues),
                                                                             Metric.memory.from(nodeValues),
                                                                             Metric.disk.from(nodeValues)),
                                                                    (long)Metric.generation.from(nodeValues),
                                                                    Metric.inService.from(nodeValues) > 0,
                                                                    clusterIsStable(node.get(), applicationNodes),
                                                                    Metric.queryRate.from(nodeValues))));

        var cluster = node.get().allocation().get().membership().cluster().id();
        var metrics = clusterMetrics.getOrDefault(cluster, ClusterMetricSnapshot.empty(at));
        metrics = metrics.withQueryRate(metrics.queryRate() + Metric.queryRate.from(nodeValues));
        metrics = metrics.withWriteRate(metrics.queryRate() + Metric.writeRate.from(nodeValues));
        clusterMetrics.put(cluster, metrics);
    }

    private Instant consumeNodeMetrics(Inspector nodeObject, ListMap<String, Double> nodeValues) {
        long timestampSecond = nodeObject.field("timestamp").asLong();
        Instant at = Instant.ofEpochMilli(timestampSecond * 1000);
        nodeObject.field("metrics").traverse((ArrayTraverser) (__, item) -> consumeMetricsItem(item, nodeValues));
        return at;
    }

    private void consumeServiceMetrics(Inspector servicesObject, ListMap<String, Double> nodeValues) {
        servicesObject.traverse((ArrayTraverser) (__, item) -> consumeServiceItem(item, nodeValues));
    }

    private void consumeServiceItem(Inspector serviceObject, ListMap<String, Double> nodeValues) {
        serviceObject.field("metrics").traverse((ArrayTraverser) (__, item) -> consumeMetricsItem(item, nodeValues));
    }

    private void consumeMetricsItem(Inspector item, ListMap<String, Double> values) {
        item.field("values").traverse((ObjectTraverser)(name, value) -> values.put(name, value.asDouble()));
    }

    private boolean clusterIsStable(Node node, NodeList applicationNodes) {
        ClusterSpec cluster = node.allocation().get().membership().cluster();
        return applicationNodes.cluster(cluster.id()).retired().isEmpty();
    }

    public static MetricsResponse empty() { return new MetricsResponse(List.of()); }

    /** The metrics this can read */
    private enum Metric {

        cpu { // a node resource

            @Override
            public List<String> metricResponseNames() { return List.of("cpu.util"); }

            @Override
            double computeFinal(ListMap<String, Double> values) {
                return values.values().stream().flatMap(List::stream).mapToDouble(v -> v).average().orElse(0) / 100; // % to ratio
            }

        },
        memory { // a node resource

            @Override
            public List<String> metricResponseNames() {
                return List.of("content.proton.resource_usage.memory.average", "mem.util");
            }

            @Override
            double computeFinal(ListMap<String, Double> values) {
                var valueList = values.get("content.proton.resource_usage.memory.average"); // prefer over mem.util
                if ( ! valueList.isEmpty()) return valueList.get(0);

                valueList = values.get("mem.util");
                if ( ! valueList.isEmpty()) return valueList.get(0) / 100; // % to ratio

                return 0;
            }

        },
        disk { // a node resource

            @Override
            public List<String> metricResponseNames() {
                return List.of("content.proton.resource_usage.disk.average", "disk.util");
            }

            @Override
            double computeFinal(ListMap<String, Double> values) {
                var valueList = values.get("content.proton.resource_usage.disk.average"); // prefer over mem.util
                if ( ! valueList.isEmpty()) return valueList.get(0);

                valueList = values.get("disk.util");
                if ( ! valueList.isEmpty()) return valueList.get(0) / 100; // % to ratio

                return 0;
            }

        },
        generation { // application config generation active on the node

            @Override
            public List<String> metricResponseNames() { return List.of("application_generation",
                                                                       "content.proton.config.generation"); }

            @Override
            double computeFinal(ListMap<String, Double> values) {
                return values.values().stream().flatMap(List::stream).mapToDouble(v -> v).min().orElse(-1);
            }

        },
        inService {

            @Override
            public List<String> metricResponseNames() { return List.of("in_service"); }

            @Override
            double computeFinal(ListMap<String, Double> values) {
                // Really a boolean. Default true. If any is oos -> oos.
                return values.values().stream().flatMap(List::stream).anyMatch(v -> v == 0) ? 0 : 1;
            }

        },
        queryRate { // queries per second

            @Override
            public List<String> metricResponseNames() {
                return List.of("queries.rate",
                               "content.proton.documentdb.matching.queries.rate");
            }

        },
        writeRate { // writes per second

            @Override
            public List<String> metricResponseNames() {
                return List.of("feed.http-requests.rate",
                               "vds.filestor.allthreads.put.count.rate",
                               "vds.filestor.allthreads.remove.count.rate",
                               "vds.filestor.allthreads.update.count.rate"); }

        };

        /**
         * The names of this metric as emitted from its source.
         * A map of the values of these names which were present in the response will
         * be provided to computeFinal to decide on a single value.
         */
        public abstract List<String> metricResponseNames();

        /** Computes the final metric value */
        double computeFinal(ListMap<String, Double> values) {
            return values.values().stream().flatMap(List::stream).mapToDouble(v -> v).sum();
        }

        public double from(ListMap<String, Double> metricValues) {
            ListMap<String, Double> values = new ListMap<>(metricValues);
            values.keySet().retainAll(metricResponseNames());
            return computeFinal(values);
        }

    }

}
