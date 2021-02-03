// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;

/**
 * Fetch metrics for a given vespa service
 *
 * @author Jo Kristian Bergum
 */
public class RemoteMetricsFetcher extends HttpMetricFetcher {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    final static String METRICS_PATH = STATE_PATH + "metrics";

    RemoteMetricsFetcher(VespaService service, int port) {
        super(service, port, METRICS_PATH);
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    public Metrics getMetrics(int fetchCount) {
        String data = "{}";
        try {
            data = getJson();
        } catch (IOException e) {
            logMessageNoResponse(errMsgNoResponse(e), fetchCount);
        }

        return createMetrics(data, fetchCount);
    }

    /**
     * Connect to remote service over http and fetch metrics
     */
    Metrics createMetrics(String data, int fetchCount) {
        Metrics remoteMetrics = new Metrics();
        try {
            remoteMetrics = parse(data);
        } catch (Exception e) {
            handleException(e, data, fetchCount);
        }

        return remoteMetrics;
    }

    private Metrics parse(String data) throws IOException {
        JsonNode o = jsonMapper.readTree(data);
        if (!(o.has("metrics"))) {
            return new Metrics(); //empty
        }

        JsonNode metrics = o.get("metrics");
        ArrayNode values;
        long timestamp;

        try {
            JsonNode snapshot = metrics.get("snapshot");
            timestamp = snapshot.get("to").asLong();
            values = (ArrayNode) metrics.get("values");
        } catch (Exception e) {
            // snapshot might not have been produced. Do not throw exception into log
            return new Metrics();
        }
        long now = System.currentTimeMillis() / 1000;
        timestamp = Metric.adjustTime(timestamp, now);
        Metrics m = new Metrics(timestamp);

        Map<DimensionId, String> noDims = Collections.emptyMap();
        Map<String, Map<DimensionId, String>> uniqueDimensions = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            JsonNode metric = values.get(i);
            String name = metric.get("name").textValue();
            String description = "";

            if (metric.has("description")) {
                description = metric.get("description").textValue();
            }

            Map<DimensionId, String> dim = noDims;
            if (metric.has("dimensions")) {
                JsonNode dimensions = metric.get("dimensions");
                StringBuilder sb = new StringBuilder();
                for (Iterator<?> it = dimensions.fieldNames(); it.hasNext(); ) {
                    String k = (String) it.next();
                    String v = dimensions.get(k).asText();
                    sb.append(toDimensionId(k)).append(v);
                }
                if ( ! uniqueDimensions.containsKey(sb.toString())) {
                    dim = new HashMap<>();
                    for (Iterator<?> it = dimensions.fieldNames(); it.hasNext(); ) {
                        String k = (String) it.next();
                        String v = dimensions.get(k).textValue();
                        dim.put(toDimensionId(k), v);
                    }
                    uniqueDimensions.put(sb.toString(), Collections.unmodifiableMap(dim));
                }
                dim = uniqueDimensions.get(sb.toString());
            }

            JsonNode aggregates = metric.get("values");
            for (Iterator<?> it = aggregates.fieldNames(); it.hasNext(); ) {
                String aggregator = (String) it.next();
                JsonNode aggregatorValue = aggregates.get(aggregator);
                if (aggregatorValue == null) {
                    throw new IllegalArgumentException("Value for aggregator '" + aggregator + "' is missing");
                }
                Number value = aggregatorValue.numberValue();
                if (value == null) {
                    throw new IllegalArgumentException("Value for aggregator '" + aggregator + "' is not a number");
                }
                StringBuilder metricName = (new StringBuilder()).append(name).append(".").append(aggregator);
                m.add(new Metric(metricName.toString(), value, timestamp, dim, description));
            }
        }

        return m;
    }
}
