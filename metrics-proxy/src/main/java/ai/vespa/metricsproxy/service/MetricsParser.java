// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;

/**
 * Fetch metrics for a given vespa service
 *
 * @author Jo Kristian Bergum
 */
public class MetricsParser {
    public interface Consumer {
        void consume(Metric metric);
    }

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static void parse(String data, Consumer consumer) throws IOException {
        parse(jsonMapper.createParser(data), consumer);
    }

    static void parse(InputStream data, Consumer consumer) throws IOException {
        parse(jsonMapper.createParser(data), consumer);
    }
    private static void parse(JsonParser parser, Consumer consumer) throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected start of object, got " + parser.currentToken());
        }

        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            if (fieldName.equals("metrics")) {
                parseMetrics(parser, consumer);
            } else {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                }
            }
        }
    }
    static private Instant parseSnapshot(JsonParser parser) throws IOException {
        if (parser.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected start of 'snapshot' object, got " + parser.currentToken());
        }
        Instant timestamp = Instant.now();
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            if (fieldName.equals("to")) {
                timestamp = Instant.ofEpochSecond(parser.getLongValue());
                timestamp = Instant.ofEpochSecond(Metric.adjustTime(timestamp.getEpochSecond(), Instant.now().getEpochSecond()));
            } else {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                }
            }
        }
        return timestamp;
    }

    static private void parseMetricValues(JsonParser parser, Instant timestamp, Consumer consumer) throws IOException {
        if (parser.getCurrentToken() != JsonToken.START_ARRAY) {
            throw new IOException("Expected start of 'metrics:values' array, got " + parser.currentToken());
        }

        Map<Long, Map<DimensionId, String>> uniqueDimensions = new HashMap<>();
        while (parser.nextToken() == JsonToken.START_OBJECT) {
            handleValue(parser, timestamp, consumer, uniqueDimensions);
        }
    }

    static private void parseMetrics(JsonParser parser, Consumer consumer) throws IOException {
        if (parser.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected start of 'metrics' object, got " + parser.currentToken());
        }
        Instant timestamp = Instant.now();
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            if (fieldName.equals("snapshot")) {
                timestamp = parseSnapshot(parser);
            } else if (fieldName.equals("values")) {
                parseMetricValues(parser, timestamp, consumer);
            } else {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                }
            }
        }
    }

    private static Map<DimensionId, String> parseDimensions(JsonParser parser,
                                                            Map<Long, Map<DimensionId, String>> uniqueDimensions) throws IOException {
        List<Map.Entry<String, String>> dims = new ArrayList<>();
        int keyHash = 0;
        int valueHash = 0;
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            if (token == JsonToken.VALUE_STRING){
                String value = parser.getValueAsString();
                dims.add(Map.entry(fieldName, value));
                keyHash ^= fieldName.hashCode();
                valueHash ^= value.hashCode();
            } else if (token == JsonToken.VALUE_NULL) {
                // TODO Should log a warning if this happens
            } else {
                throw new IllegalArgumentException("Dimension '" + fieldName + "' must be a string");
            }
        }
        Long uniqueKey = (((long) keyHash) << 32) | (valueHash & 0xffffffffL);
        return uniqueDimensions.computeIfAbsent(uniqueKey, key -> dims.stream().collect(Collectors.toUnmodifiableMap(e -> toDimensionId(e.getKey()), Map.Entry::getValue)));
    }
    private static List<Map.Entry<String, Number>> parseValues(String prefix, JsonParser parser) throws IOException {
        List<Map.Entry<String, Number>> metrics = new ArrayList<>();
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            String metricName = prefix + fieldName;
            if (token == JsonToken.VALUE_NUMBER_INT) {
                metrics.add(Map.entry(metricName, parser.getLongValue()));
            } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                metrics.add(Map.entry(metricName, parser.getValueAsDouble()));
            } else {
                throw new IllegalArgumentException("Value for aggregator '" + fieldName + "' is not a number");
            }
        }
        return metrics;
    }
    static private void handleValue(JsonParser parser, Instant timestamp, Consumer consumer,
                                    Map<Long, Map<DimensionId, String>> uniqueDimensions) throws IOException {
        String name = "";
        String description = "";
        Map<DimensionId, String> dim = Map.of();
        List<Map.Entry<String, Number>> values = List.of();
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            if (fieldName.equals("name")) {
                name = parser.getText();
            } else if (fieldName.equals("description")) {
                description = parser.getText();
            } else if (fieldName.equals("dimensions")) {
                dim = parseDimensions(parser, uniqueDimensions);
            } else if (fieldName.equals("values")) {
                values = parseValues(name+".", parser);
            } else {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                }
            }
        }
        for (Map.Entry<String, Number> value : values) {
            consumer.consume(new Metric(MetricId.toMetricId(value.getKey()), value.getValue(), timestamp, dim, description));
        }
    }
}
