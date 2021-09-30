// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    static final String STATUS_CODE_KEY = "status_code";
    static final String STATUS_MSG_KEY  = "status_msg";
    static final String METRICS_KEY     = "metrics";
    static final String DIMENSIONS_KEY  = "dimensions";

    static final String PACKET_SEPARATOR = "\n\n";

    private final StateMonitor monitor;
    private final Timer timer;
    private final SnapshotProvider snapshotProvider;
    private final String applicationName;

    @Inject
    public MetricsPacketsHandler(StateMonitor monitor,
                                 Timer timer,
                                 ComponentRegistry<SnapshotProvider> snapshotProviders,
                                 MetricsPacketsHandlerConfig config) {
        this.monitor = monitor;
        this.timer = timer;
        snapshotProvider = getSnapshotProviderOrThrow(snapshotProviders);
        applicationName = config.application();
    }


    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler handler) {
        new ResponseDispatch() {
            @Override
            protected Response newResponse() {
                Response response = new Response(Response.Status.OK);
                response.headers().add(HttpHeaders.Names.CONTENT_TYPE, "application/json");
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
            if (query != null && query.equals("array-formatted")) {
                return getMetricsArray();
            }
            String output = jsonToString(getStatusPacket()) + getAllMetricsPackets() + "\n";
            return output.getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Bad JSON construction.", e);
        }
    }

    private byte[] getMetricsArray() throws JsonProcessingException {
        ObjectNode root = jsonMapper.createObjectNode();
        ArrayNode jsonArray = jsonMapper.createArrayNode();
        jsonArray.add(getStatusPacket());
        getPacketsForSnapshot(getSnapshot(), applicationName, timer.currentTimeMillis())
                .forEach(jsonArray::add);
        MetricGatherer.getAdditionalMetrics().forEach(jsonArray::add);
        root.set("metrics", jsonArray);
        return jsonToString(root)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Exactly one status packet is added to the response.
     */
    private JsonNode getStatusPacket() {
        ObjectNode packet = jsonMapper.createObjectNode();
        packet.put(APPLICATION_KEY, applicationName);

        StateMonitor.Status status = monitor.status();
        packet.put(STATUS_CODE_KEY, status.ordinal());
        packet.put(STATUS_MSG_KEY, status.name());
        return packet;
    }

    private static String jsonToString(JsonNode jsonObject) throws JsonProcessingException {
        return jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(jsonObject);
    }

    private String getAllMetricsPackets() throws JsonProcessingException {
        StringBuilder ret = new StringBuilder();
        List<JsonNode> metricsPackets = getPacketsForSnapshot(getSnapshot(), applicationName, timer.currentTimeMillis());
        for (JsonNode packet : metricsPackets) {
            ret.append(PACKET_SEPARATOR); // For legibility and parsing in unit tests
            ret.append(jsonToString(packet));
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

    private void addMetaData(long timestamp, String application, ObjectNode packet) {
        packet.put(APPLICATION_KEY, application);
        packet.put(TIMESTAMP_KEY, TimeUnit.MILLISECONDS.toSeconds(timestamp));
    }

    private void addDimensions(MetricDimensions metricDimensions, ObjectNode packet) {
        if (metricDimensions == null) return;

        Iterator<Map.Entry<String, String>> dimensionsIterator = metricDimensions.iterator();
        if (dimensionsIterator.hasNext()) {
            ObjectNode jsonDim = jsonMapper.createObjectNode();
            packet.set(DIMENSIONS_KEY, jsonDim);
            for (Map.Entry<String, String> dimensionEntry : metricDimensions) {
                jsonDim.put(dimensionEntry.getKey(), dimensionEntry.getValue());
            }
        }
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
                        .put(name + ".max", sanitizeDouble(gauge.getMax()));
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

}
