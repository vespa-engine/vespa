// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumes a response from the metrics/v2 API and populates the fields of this with the resulting values
 *
 * @author bratseth
 */
public class MetricsResponse {

    private final List<NodeMetrics.MetricValue> metricValues = new ArrayList<>();

    public MetricsResponse(byte[] response) {
        this(SlimeUtils.jsonToSlime(response));
    }

    public MetricsResponse(String response) {
        this(SlimeUtils.jsonToSlime(response));
    }

    public List<NodeMetrics.MetricValue> metrics() { return metricValues; }

    private MetricsResponse(Slime response) {
        Inspector root = response.get();
        Inspector nodes = root.field("nodes");
        nodes.traverse((ArrayTraverser)(__, node) -> consumeNode(node));
    }

    private void consumeNode(Inspector node) {
        String hostname = node.field("hostname").asString();
        consumeNodeMetrics(hostname, node.field("node"));
        consumeServiceMetrics(hostname, node.field("services"));
    }

    private void consumeNodeMetrics(String hostname, Inspector node) {
        long timestampSecond = node.field("timestamp").asLong();
        Map<String, Double> values = consumeMetrics(node.field("metrics"));
        for (Resource resource : Resource.values())
            addMetricIfPresent(hostname, resource, timestampSecond, values);
    }

    private void addMetricIfPresent(String hostname, Resource resource, long timestampSecond, Map<String, Double> values) {
        if (values.containsKey(resource.metricName()))
            metricValues.add(new NodeMetrics.MetricValue(hostname,
                                                         resource.metricName(),
                                                         timestampSecond,
                                                         values.get(resource.metricName())));
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

}
