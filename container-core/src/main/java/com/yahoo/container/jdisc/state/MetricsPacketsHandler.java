// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import ai.vespa.metrics.set.InfrastructureMetricSet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.component.annotation.Inject;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpHeaders;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yahoo.container.jdisc.state.JsonUtil.sanitizeDouble;
import static com.yahoo.container.jdisc.state.StateHandler.getSnapshotProviderOrThrow;

/**
 * This handler outputs metrics in a json-like format, consisting of a series of metrics packets.
 * Each packet is a json object but there is no outer array or object that wraps the packets.
 * To reduce the amount of output, a packet contains all metrics that share the same set of dimensions.
 *
 * This handler is not set up by default, but can be added to the applications's services configuration.
 *
 * This handler is protocol agnostic, so it cannot discriminate between e.g. http request
 * methods (get/head/post etc.).
 *
 * Based on {@link StateHandler}.
 *
 * @author gjoranv
 */
public class MetricsPacketsHandler extends AbstractRequestHandler {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    static final String APPLICATION_KEY = "application";
    static final String TIMESTAMP_KEY   = "timestamp";
    static final String METRICS_KEY     = "metrics";
    static final String DIMENSIONS_KEY  = "dimensions";

    static final String PACKET_SEPARATOR = "\n\n";

    private final Timer timer;
    private final SnapshotProvider snapshotProvider;
    private final String applicationName;
    private final String hostDimension;
    private final Map<String, Set<String>> metricSets;

    @Inject
    public MetricsPacketsHandler(Timer timer,
                                 ComponentRegistry<SnapshotProvider> snapshotProviders,
                                 MetricsPacketsHandlerConfig config) {
        this.timer = timer;
        snapshotProvider = getSnapshotProviderOrThrow(snapshotProviders);
        applicationName = config.application();
        hostDimension = config.hostname();
        metricSets = getMetricSets();
    }


    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler handler) {
        new ResponseDispatch() {
            @Override
            protected Response newResponse() {
                Response response = new Response(Response.Status.OK);
                response.headers().add(HttpHeaders.Names.CONTENT_TYPE, getContentType(request.getUri().getQuery()));
                return response;
            }

            @Override
            protected Iterable<ByteBuffer> responseContent() {
                return Collections.singleton(ByteBuffer.wrap(buildMetricOutput(request.getUri().getQuery())));
            }
        }.dispatch(handler);

        return null;
    }

    private byte[] buildMetricOutput(String query) {
        try {
            var queryMap = parseQuery(query);
            var metricSetId = queryMap.get("metric-set");
            var format = queryMap.get("format");

            // TODO: Remove "array-formatted"
            if ("array".equals(format) || queryMap.containsKey("array-formatted")) {
                return getMetricsArray(metricSetId);
            }
            if ("prometheus".equals(format)) {
                return buildPrometheusOutput();
            }

            String output = getAllMetricsPackets(metricSetId) + "\n";
            return output.getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Bad JSON construction.", e);
        } catch (IOException e) {
            throw new RuntimeException("Unexcpected IOException.", e);
        }
    }

    private byte[] getMetricsArray(String metricSetId) throws JsonProcessingException {
        ObjectNode root = jsonMapper.createObjectNode();
        ArrayNode jsonArray = jsonMapper.createArrayNode();
        getPacketsForSnapshot(getSnapshot(), metricSetId, applicationName, timer.currentTimeMillis())
                .forEach(jsonArray::add);
        MetricGatherer.getAdditionalMetrics().forEach(jsonArray::add);
        root.set("metrics", jsonArray);
        return jsonToString(root)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns metrics in Prometheus format
     */
    private byte[] buildPrometheusOutput() throws IOException {
        return PrometheusHelper.buildPrometheusOutput(getSnapshot(), applicationName, timer.currentTimeMillis());
    }

    private static String jsonToString(JsonNode jsonObject) throws JsonProcessingException {
        return jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(jsonObject);
    }

    private String getAllMetricsPackets(String metricSetId) throws JsonProcessingException {
        StringBuilder ret = new StringBuilder();
        List<JsonNode> metricsPackets = getPacketsForSnapshot(getSnapshot(), metricSetId, applicationName, timer.currentTimeMillis());
        String delimiter = "";
        for (JsonNode packet : metricsPackets) {
            ret.append(delimiter); // For legibility and parsing in unit tests
            ret.append(jsonToString(packet));
            delimiter = PACKET_SEPARATOR;
        }
        return ret.toString();
    }

    private MetricSnapshot getSnapshot() {
        return snapshotProvider.latestSnapshot();
    }

    private List<JsonNode> getPacketsForSnapshot(MetricSnapshot metricSnapshot, String application, long timestamp) {
        if (metricSnapshot == null) return Collections.emptyList();

        List<JsonNode> packets = new ArrayList<>();

        for (Map.Entry<MetricDimensions, MetricSet> snapshotEntry : metricSnapshot) {
            MetricDimensions metricDimensions = snapshotEntry.getKey();
            MetricSet metricSet = snapshotEntry.getValue();

            ObjectNode packet = jsonMapper.createObjectNode();
            addMetaData(timestamp, application, packet);
            addDimensions(metricDimensions, packet);
            addMetrics(metricSet, packet);
            packets.add(packet);
        }
        return packets;
    }

    private List<JsonNode> getPacketsForSnapshot(MetricSnapshot metricSnapshot, String metricSetId, String application, long timestamp) {
        if (metricSnapshot == null) return Collections.emptyList();
        if (metricSetId == null) return getPacketsForSnapshot(metricSnapshot, application, timestamp);
        Set<String> configuredMetrics = metricSets.getOrDefault(metricSetId, Collections.emptySet());
        List<JsonNode> packets = new ArrayList<>();

        for (Map.Entry<MetricDimensions, MetricSet> snapshotEntry : metricSnapshot) {
            MetricDimensions metricDimensions = snapshotEntry.getKey();
            MetricSet metricSet = snapshotEntry.getValue();

            ObjectNode packet = jsonMapper.createObjectNode();
            addMetaData(timestamp, application, packet);
            addDimensions(metricDimensions, packet);
            var metrics = getMetrics(metricSet);
            metrics.keySet().retainAll(configuredMetrics);
            if (!metrics.isEmpty()) {
                addMetrics(metrics, packet);
                packets.add(packet);
            }
        }
        return packets;
    }

    private void addMetaData(long timestamp, String application, ObjectNode packet) {
        packet.put(APPLICATION_KEY, application);
        packet.put(TIMESTAMP_KEY, TimeUnit.MILLISECONDS.toSeconds(timestamp));
    }

    private void addDimensions(MetricDimensions metricDimensions, ObjectNode packet) {
        if (metricDimensions == null && hostDimension.isEmpty()) return;

        ObjectNode jsonDim = jsonMapper.createObjectNode();
        packet.set(DIMENSIONS_KEY, jsonDim);
        Iterable<Map.Entry<String, String>> dimensionIterator = metricDimensions == null ? Set.of() : metricDimensions;
        for (Map.Entry<String, String> dimensionEntry : dimensionIterator) {
            jsonDim.put(dimensionEntry.getKey(), dimensionEntry.getValue());
        }
        if (!hostDimension.isEmpty() && !jsonDim.has("host"))
            jsonDim.put("host", hostDimension);
    }

    private void addMetrics(MetricSet metricSet, ObjectNode packet) {
        ObjectNode metrics = jsonMapper.createObjectNode();
        packet.set(METRICS_KEY, metrics);
        for (Map.Entry<String, MetricValue> metric : metricSet) {
            String name = metric.getKey();
            MetricValue value = metric.getValue();
            if (value instanceof CountMetric) {
                metrics.put(name + ".count", ((CountMetric) value).getCount());
            } else if (value instanceof GaugeMetric) {
                GaugeMetric gauge = (GaugeMetric) value;
                metrics.put(name + ".average", sanitizeDouble(gauge.getAverage()))
                        .put(name + ".last", sanitizeDouble(gauge.getLast()))
                        .put(name + ".max", sanitizeDouble(gauge.getMax()))
                        .put(name + ".min", sanitizeDouble(gauge.getMin()))
                        .put(name + ".sum", sanitizeDouble(gauge.getSum()))
                        .put(name + ".count", gauge.getCount());
                if (gauge.getPercentiles().isPresent()) {
                    for (Tuple2<String, Double> prefixAndValue : gauge.getPercentiles().get()) {
                        metrics.put(name + "." + prefixAndValue.first + "percentile", prefixAndValue.second.doubleValue());
                    }
                }
            } else {
                throw new UnsupportedOperationException("Unknown metric class: " + value.getClass().getName());
            }
        }
    }

    private Map<String, Number> getMetrics(MetricSet metricSet) {
        var metrics = new HashMap<String, Number>();
        for (Map.Entry<String, MetricValue> metric : metricSet) {
            String name = metric.getKey();
            MetricValue value = metric.getValue();
            if (value instanceof CountMetric) {
                metrics.put(name + ".count", ((CountMetric) value).getCount());
            } else if (value instanceof GaugeMetric) {
                GaugeMetric gauge = (GaugeMetric) value;
                metrics.put(name + ".average", sanitizeDouble(gauge.getAverage()));
                metrics.put(name + ".last", sanitizeDouble(gauge.getLast()));
                metrics.put(name + ".max", sanitizeDouble(gauge.getMax()));
                metrics.put(name + ".min", sanitizeDouble(gauge.getMin()));
                metrics.put(name + ".sum", sanitizeDouble(gauge.getSum()));
                metrics.put(name + ".count", gauge.getCount());
                if (gauge.getPercentiles().isPresent()) {
                    for (Tuple2<String, Double> prefixAndValue : gauge.getPercentiles().get()) {
                        metrics.put(name + "." + prefixAndValue.first + "percentile", prefixAndValue.second.doubleValue());
                    }
                }
            } else {
                throw new UnsupportedOperationException("Unknown metric class: " + value.getClass().getName());
            }
        }
        return metrics;
    }

    private void addMetrics(Map<String, Number> metrics, ObjectNode packet) {
        ObjectNode metricsObject = jsonMapper.createObjectNode();
        packet.set(METRICS_KEY, metricsObject);
        metrics.forEach((name, value) -> {
            if (value instanceof Double) metricsObject.put(name, (Double) value);
            else metricsObject.put(name, (Long) value);
        });
    }

    private String getContentType(String query) {
        if ("format=prometheus".equals(query)) {
            return "text/plain;charset=utf-8";
        }
        return "application/json";
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null) return Map.of();
        return Arrays.stream(query.split("&"))
                .map(s -> s.split("="))
                .collect(Collectors.toMap(s -> s[0], s -> s.length < 2 ? "" : s[1]));
    }

    private Map<String, Set<String>> getMetricSets() {
        // For now - single infrastructure metric set
        return Map.of(
                InfrastructureMetricSet.infrastructureMetricSet.getId(), InfrastructureMetricSet.infrastructureMetricSet.getMetrics().keySet()
        );
    }
}
