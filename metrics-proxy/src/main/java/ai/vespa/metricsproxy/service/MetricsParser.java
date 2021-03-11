// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
public class MetricsParser {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    static Metrics parse(String data) throws IOException {
        JsonParser parser = jsonMapper.createParser(data);

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected start of object, got " + parser.currentToken());
        }

        Metrics metrics  = new Metrics();
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            if (fieldName.equals("metrics")) {
                metrics = parseMetrics(parser);
            } else {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                }
            }
        }
        return metrics;
    }

    static private Metrics parseSnapshot(JsonParser parser) throws IOException {
        if (parser.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected start of 'snapshot' object, got " + parser.currentToken());
        }
        Metrics metrics = new Metrics();
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            if (fieldName.equals("to")) {
                long timestamp = parser.getLongValue();
                long now = System.currentTimeMillis() / 1000;
                timestamp = Metric.adjustTime(timestamp, now);
                metrics = new Metrics(timestamp);
            } else {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                }
            }
        }
        return metrics;
    }

    static private void parseValues(JsonParser parser, Metrics metrics) throws IOException {
        if (parser.getCurrentToken() != JsonToken.START_ARRAY) {
            throw new IOException("Expected start of 'metrics:values' array, got " + parser.currentToken());
        }

        Map<String, Map<DimensionId, String>> uniqueDimensions = new HashMap<>();
        while (parser.nextToken() == JsonToken.START_OBJECT) {
            // read everything from this START_OBJECT to the matching END_OBJECT
            // and return it as a tree model ObjectNode
            JsonNode value = jsonMapper.readTree(parser);
            handleValue(value, metrics.getTimeStamp(), metrics, uniqueDimensions);

            // do whatever you need to do with this object
        }
    }

    static private Metrics parseMetrics(JsonParser parser) throws IOException {
        if (parser.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected start of 'metrics' object, got " + parser.currentToken());
        }
        Metrics metrics = new Metrics();
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            if (fieldName.equals("snapshot")) {
                metrics = parseSnapshot(parser);
            } else if (fieldName.equals("values")) {
                parseValues(parser, metrics);
            } else {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                }
            }
        }
        return metrics;
    }

    static private void handleValue(JsonNode metric, long timestamp, Metrics metrics, Map<String, Map<DimensionId, String>> uniqueDimensions) {
        String name = metric.get("name").textValue();
        String description = "";

        if (metric.has("description")) {
            description = metric.get("description").textValue();
        }

        Map<DimensionId, String> dim = Collections.emptyMap();
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
            metrics.add(new Metric(metricName.toString(), value, timestamp, dim, description));
        }
    }
}
