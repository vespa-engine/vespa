// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

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
import com.yahoo.metrics.MetricsPresentationConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.yahoo.container.jdisc.state.StateHandler.getSnapshotPreprocessor;

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
    static final String APPLICATION_KEY = "application";
    static final String TIMESTAMP_KEY   = "timestamp";
    static final String STATUS_CODE_KEY = "status_code";
    static final String STATUS_MSG_KEY  = "status_msg";
    static final String METRICS_KEY     = "metrics";
    static final String DIMENSIONS_KEY  = "dimensions";

    static final String PACKET_SEPARATOR = "\n\n";

    private final StateMonitor monitor;
    private final Timer timer;
    private final SnapshotProvider snapshotPreprocessor;
    private final String applicationName;

    @Inject
    public MetricsPacketsHandler(StateMonitor monitor,
                                 Timer timer,
                                 ComponentRegistry<SnapshotProvider> preprocessors,
                                 MetricsPresentationConfig presentation,
                                 MetricsPacketsHandlerConfig config) {
        this.monitor = monitor;
        this.timer = timer;
        snapshotPreprocessor = getSnapshotPreprocessor(preprocessors, presentation);
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
        } catch (JSONException e) {
            throw new RuntimeException("Bad JSON construction.", e);
        }
    }

    private byte[] getMetricsArray() throws JSONException {
        JSONObject root = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(getStatusPacket());
        getPacketsForSnapshot(getSnapshot(), applicationName, timer.currentTimeMillis())
                .forEach(jsonArray::put);
        MetricGatherer.getAdditionalMetrics().forEach(jsonArray::put);
        root.put("metrics", jsonArray);
        return jsonToString(root)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Exactly one status packet is added to the response.
     */
    private JSONObject getStatusPacket() throws JSONException {
        JSONObject packet = new JSONObjectWithLegibleException();
        packet.put(APPLICATION_KEY, applicationName);

        StateMonitor.Status status = monitor.status();
        packet.put(STATUS_CODE_KEY, status.ordinal());
        packet.put(STATUS_MSG_KEY, status.name());
        return packet;
    }

    private String jsonToString(JSONObject jsonObject) throws JSONException {
        return jsonObject.toString(4);
    }

    private String getAllMetricsPackets() throws JSONException {
        StringBuilder ret = new StringBuilder();
        List<JSONObject> metricsPackets = getPacketsForSnapshot(getSnapshot(), applicationName, timer.currentTimeMillis());
        for (JSONObject packet : metricsPackets) {
            ret.append(PACKET_SEPARATOR); // For legibility and parsing in unit tests
            ret.append(jsonToString(packet));
        }
        return ret.toString();
    }

    private MetricSnapshot getSnapshot() {
        if (snapshotPreprocessor == null) {
            return monitor.snapshot();
        } else {
            return snapshotPreprocessor.latestSnapshot();
        }
    }

    private List<JSONObject> getPacketsForSnapshot(MetricSnapshot metricSnapshot, String application, long timestamp) throws JSONException {
        if (metricSnapshot == null) return Collections.emptyList();

        List<JSONObject> packets = new ArrayList<>();

        for (Map.Entry<MetricDimensions, MetricSet> snapshotEntry : metricSnapshot) {
            MetricDimensions metricDimensions = snapshotEntry.getKey();
            MetricSet metricSet = snapshotEntry.getValue();

            JSONObjectWithLegibleException packet = new JSONObjectWithLegibleException();
            addMetaData(timestamp, application, packet);
            addDimensions(metricDimensions, packet);
            addMetrics(metricSet, packet);
            packets.add(packet);
        }
        return packets;
    }

    private void addMetaData(long timestamp, String application, JSONObjectWithLegibleException packet) {
        packet.put(APPLICATION_KEY, application);
        packet.put(TIMESTAMP_KEY, TimeUnit.MILLISECONDS.toSeconds(timestamp));
    }

    private void addDimensions(MetricDimensions metricDimensions, JSONObjectWithLegibleException packet) throws JSONException {
        Iterator<Map.Entry<String, String>> dimensionsIterator = metricDimensions.iterator();
        if (dimensionsIterator.hasNext()) {
            JSONObject jsonDim = new JSONObjectWithLegibleException();
            packet.put(DIMENSIONS_KEY, jsonDim);
            for (Map.Entry<String, String> dimensionEntry : metricDimensions) {
                jsonDim.put(dimensionEntry.getKey(), dimensionEntry.getValue());
            }
        }
    }

    private void addMetrics(MetricSet metricSet, JSONObjectWithLegibleException packet) throws JSONException {
        JSONObjectWithLegibleException metrics = new JSONObjectWithLegibleException();
        packet.put(METRICS_KEY, metrics);
        for (Map.Entry<String, MetricValue> metric : metricSet) {
            String name = metric.getKey();
            MetricValue value = metric.getValue();
            if (value instanceof CountMetric) {
                metrics.put(name + ".count", ((CountMetric) value).getCount());
            } else if (value instanceof GaugeMetric) {
                GaugeMetric gauge = (GaugeMetric) value;
                metrics.put(name + ".average", gauge.getAverage())
                        .put(name + ".last", gauge.getLast())
                        .put(name + ".max", gauge.getMax());
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
