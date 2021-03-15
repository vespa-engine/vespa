// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.hosted.provision.NodeRepository;

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
    public MetricsResponse(String response, NodeList applicationNodes, NodeRepository nodeRepository) {
        this(SlimeUtils.jsonToSlime(response), applicationNodes, nodeRepository);
    }

    public MetricsResponse(Collection<Pair<String, NodeMetricSnapshot>> metrics) {
        this.nodeMetrics = metrics;
    }

    private MetricsResponse(Slime response, NodeList applicationNodes, NodeRepository nodeRepository) {
        nodeMetrics = new ArrayList<>();
        Inspector root = response.get();
        Inspector nodes = root.field("nodes");
        nodes.traverse((ArrayTraverser)(__, node) -> consumeNode(node, applicationNodes, nodeRepository));
    }

    public Collection<Pair<String, NodeMetricSnapshot>> nodeMetrics() { return nodeMetrics; }

    public Map<ClusterSpec.Id, ClusterMetricSnapshot> clusterMetrics() { return clusterMetrics; }

    private void consumeNode(Inspector nodeObject, NodeList applicationNodes, NodeRepository nodeRepository) {
        String hostname = nodeObject.field("hostname").asString();
        Optional<Node> node = applicationNodes.stream().filter(n -> n.hostname().equals(hostname)).findAny();
        if (node.isEmpty()) return; // Node is not part of this cluster any more

        ListMap<String, Double> nodeValues = new ListMap<>();
        Instant at = consumeNodeMetrics(nodeObject.field("node"), nodeValues);
        consumeServiceMetrics(nodeObject.field("services"), nodeValues);

        nodeMetrics.add(new Pair<>(hostname, new NodeMetricSnapshot(at,
                                                                    Metric.cpu.from(nodeValues),
                                                                    Metric.memory.from(nodeValues),
                                                                    Metric.disk.from(nodeValues),
                                                                    (long)Metric.generation.from(nodeValues),
                                                                    Metric.inService.from(nodeValues) > 0,
                                                                    clusterIsStable(node.get(), applicationNodes, nodeRepository),
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

    private boolean clusterIsStable(Node node, NodeList applicationNodes, NodeRepository nodeRepository) {
        ClusterSpec cluster = node.allocation().get().membership().cluster();
        return Autoscaler.stable(applicationNodes.cluster(cluster.id()), nodeRepository);
    }

    public static MetricsResponse empty() { return new MetricsResponse(List.of()); }

    /** The metrics this can read */
    private enum Metric {

        cpu { // a node resource
            public List<String> metricResponseNames() { return List.of("cpu.util"); }
            double computeFinal(List<Double> values) {
                return values.stream().mapToDouble(v -> v).average().orElse(0) / 100; // % to ratio
            }
        },
        memory { // a node resource
            public List<String> metricResponseNames() { return List.of("mem.util"); }
            double computeFinal(List<Double> values) {
                return values.stream().mapToDouble(v -> v).average().orElse(0) / 100; // % to ratio
            }
        },
        disk { // a node resource
            public List<String> metricResponseNames() { return List.of("disk.util"); }
            double computeFinal(List<Double> values) {
                return values.stream().mapToDouble(v -> v).average().orElse(0) / 100; // % to ratio
            }
        },
        generation { // application config generation active on the node
            public List<String> metricResponseNames() { return List.of("application_generation"); }
            double computeFinal(List<Double> values) {
                return values.stream().mapToDouble(v -> v).min().orElse(-1);
            }
        },
        inService {
            public List<String> metricResponseNames() { return List.of("in_service"); }
            double computeFinal(List<Double> values) {
                // Really a boolean. Default true. If any is oos -> oos.
                return values.stream().anyMatch(v -> v == 0) ? 0 : 1;
            }
        },
        queryRate { // queries per second
            public List<String> metricResponseNames() {
                return List.of("queries.rate",
                               "content.proton.documentdb.matching.queries.rate");
            }
        },
        writeRate { // writes per second
            public List<String> metricResponseNames() {
                return List.of("feed.http-requests.rate",
                               "vds.filestor.alldisks.allthreads.put.sum.count.rate",
                               "vds.filestor.alldisks.allthreads.remove.sum.count.rate",
                               "vds.filestor.alldisks.allthreads.update.sum.count.rate"); }
        };

        /** The name of this metric as emitted from its source */
        public abstract List<String> metricResponseNames();

        double computeFinal(List<Double> values) { return values.stream().mapToDouble(v -> v).sum(); }

        public double from(ListMap<String, Double> metricValues) {
            // Multiple metric names may contribute to the same logical metric.
            // Usually one per service, but we aggregate here to not require that.
            List<Double> values = new ArrayList<>(1);
            for (String metricName : metricResponseNames()) {
                List<Double> valuesForName = metricValues.get(metricName);
                if (valuesForName == null) continue;
                values.addAll(valuesForName);
            }
            return computeFinal(values);
        }

    }

}
