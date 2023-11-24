// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import com.yahoo.json.Jackson;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;

/**
 * Fetch metrics for a given vespa service
 *
 * @author Jo Kristian Bergum
 */
public class MetricsParser {
    public interface Collector {
        void accept(Metric metric);
    }

    private static final ObjectMapper jsonMapper = Jackson.mapper();

    public static void parse(String data, Collector consumer) throws IOException {
        try (JsonParser parser = jsonMapper.createParser(data)) {
            parse(parser, consumer);
        }
    }

    static void parse(InputStream data, Collector consumer) throws IOException {
        try (JsonParser parser = jsonMapper.createParser(data)) {
            parse(parser, consumer);
        }
    }

    // Top level 'metrics' object, with e.g. 'time', 'status' and 'metrics'.
    private static void parse(JsonParser parser, Collector consumer) throws IOException {
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

    // 'metrics' object with 'snapshot' and 'values' arrays
    static private void parseMetrics(JsonParser parser, Collector consumer) throws IOException {
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

    // 'values' array
    static private void parseMetricValues(JsonParser parser, Instant timestamp, Collector consumer) throws IOException {
        if (parser.getCurrentToken() != JsonToken.START_ARRAY) {
            throw new IOException("Expected start of 'metrics:values' array, got " + parser.currentToken());
        }

        Map<Set<Dimension>, Map<DimensionId, String>> uniqueDimensions = new HashMap<>();
        while (parser.nextToken() == JsonToken.START_OBJECT) {
            handleValue(parser, timestamp, consumer, uniqueDimensions);
        }
    }

    // One item in the 'values' array, where each item has 'name', 'values' and 'dimensions'
    static private void handleValue(JsonParser parser, Instant timestamp, Collector consumer,
                                    Map<Set<Dimension>, Map<DimensionId, String>> uniqueDimensions) throws IOException {
        String name = "";
        String description = "";
        Map<DimensionId, String> dim = Map.of();
        List<Map.Entry<String, Number>> values = List.of();
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            switch (fieldName) {
                case "name" -> name = parser.getText();
                case "description" -> description = parser.getText();
                case "dimensions" -> dim = parseDimensions(parser, uniqueDimensions);
                case "values" -> values = parseValues(parser);
                default -> {
                    if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                        parser.skipChildren();
                    }
                }
            }
        }
        if (name.isEmpty()) {
            throw new IOException("missing name for entry in 'values' array");
        }
        for (Map.Entry<String, Number> value : values) {
            consumer.accept(new Metric(MetricId.toMetricId(name+"."+value.getKey()), value.getValue(), timestamp, dim, description));
        }
    }

    private static Map<DimensionId, String> parseDimensions(JsonParser parser,
                                                            Map<Set<Dimension>, Map<DimensionId, String>> uniqueDimensions) throws IOException {

        Set<Dimension> dimensions = new HashSet<>();

        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();

            if (token == JsonToken.VALUE_STRING){
                String value = parser.getValueAsString();
                dimensions.add(Dimension.of(fieldName, value));
            } else if (token == JsonToken.VALUE_NULL) {
                // TODO Should log a warning if this happens
            } else {
                throw new IllegalArgumentException("Dimension '" + fieldName + "' must be a string");
            }
        }
        return uniqueDimensions.computeIfAbsent(dimensions,
                                                key -> dimensions.stream().collect(Collectors.toUnmodifiableMap(
                                                        dim -> toDimensionId(dim.id), dim -> dim.value)));
    }

    record Dimension(String id, String value) {
        static Dimension of(String id, String value) {
            return new Dimension(id, value);
        }
    }

    private static List<Map.Entry<String, Number>> parseValues(JsonParser parser) throws IOException {
        List<Map.Entry<String, Number>> metrics = new ArrayList<>();
        for (parser.nextToken(); parser.getCurrentToken() != JsonToken.END_OBJECT; parser.nextToken()) {
            String fieldName = parser.getCurrentName();
            JsonToken token = parser.nextToken();
            String metricName = fieldName;
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

}
