// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.text.Text;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.nio.charset.StandardCharsets;


/**
 * @author olaa
 */
public class PrometheusHelper {

    private static final String HELP_LINE = "# HELP %s\n# TYPE %s untyped\n";
    private static final String METRIC_LINE = "%s{%s} %s %d\n";
    private static final String DIMENSION_KEY = "dimensions";
    private static final String METRIC_KEY = "metrics";
    private static final String APPLICATION_KEY = "application";

    protected static byte[] buildPrometheusOutput(List<JsonNode> metrics, long timestamp) throws IOException {
        var outputStream = new ByteArrayOutputStream();

        for (var metric : metrics) {
            var metricDimensions = metric.get(DIMENSION_KEY);
            var dimensionBuilder = new StringBuilder();
            for (var it = metricDimensions.fieldNames(); it.hasNext(); ) {
                var dimension = it.next();
                dimensionBuilder
                        .append(sanitize(dimension))
                        .append("=\"")
                        .append(metricDimensions.get(dimension).asText())
                        .append("\",");
            }
            var application = metric.get(APPLICATION_KEY).asText();
            dimensionBuilder.append("vespa_service=\"").append(application).append("\",");
            var dimensions = dimensionBuilder.toString();
            var metricValues = metric.get(METRIC_KEY);
            for (var it = metricValues.fieldNames(); it.hasNext(); ) {
                var metricName = it.next();
                var metricVal = metricValues.get(metricName).numberValue();
                outputStream.write(getMetricLines(sanitize(metricName), dimensions, metricVal, timestamp));
            }
        }
        return outputStream.toByteArray();
    }

    private static byte[] getMetricLines(String metricName, String dimensions, Number value, long timestamp) {
        return (Text.format(HELP_LINE, metricName, metricName) +
                Text.format(METRIC_LINE, metricName, dimensions, value, timestamp)).getBytes(StandardCharsets.UTF_8);
    }

    private static String sanitize(String name) {
        return name.replaceAll("([-.])", "_");
    }

}
