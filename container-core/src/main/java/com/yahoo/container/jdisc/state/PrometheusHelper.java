// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import static com.yahoo.container.jdisc.state.JsonUtil.sanitizeDouble;

/**
 * @author olaa
 */
public class PrometheusHelper {

    private static final String HELP_LINE = "# HELP %s \n# TYPE %s untyped\n";
    private static final String METRIC_LINE = "%s{%s} %s %d\n";

    protected static byte[] buildPrometheusOutput(MetricSnapshot metricSnapshot, String application, long timestamp) throws IOException {
        var outputStream = new ByteArrayOutputStream();

        for (Map.Entry<MetricDimensions, MetricSet> snapshotEntry : metricSnapshot) {
            var metricDimensions = snapshotEntry.getKey();
            var metricSet = snapshotEntry.getValue();

            var dimensionBuilder = new StringBuilder();
            for (var dimension : metricDimensions) {
                dimensionBuilder
                        .append(sanitize(dimension.getKey()))
                        .append("=\"")
                        .append(dimension.getValue())
                        .append("\",");
            }
            dimensionBuilder.append("vespa_service=\"").append(application).append("\",");
            var dimensions = dimensionBuilder.toString();

            for (var metric : metricSet) {
                var metricName = metric.getKey();
                var metricValue = metric.getValue();

                if (metricValue instanceof CountMetric) {
                    var sanitizedMetricName = getSanitizedMetricName(metricName, "count");
                    var value = ((CountMetric) metricValue).getCount();
                    outputStream.write(getMetricLines(sanitizedMetricName, dimensions, value, timestamp));
                } else if (metricValue instanceof GaugeMetric) {
                    var gauge = (GaugeMetric) metricValue;
                    writeGaugeMetrics(outputStream, metricName, gauge, dimensions, timestamp);
                }
            }
        }
        return outputStream.toByteArray();
    }

    private static void writeGaugeMetrics(OutputStream outputStream, String metricName, GaugeMetric gaugeMetric, String dimensions, long timestamp) throws IOException {
        var sanitizedMetricName = getSanitizedMetricName(metricName, "last");
        var value = sanitizeDouble(gaugeMetric.getLast());
        outputStream.write(getMetricLines(sanitizedMetricName, dimensions, value, timestamp));

        /*
        For now - only push "last" value - to limit metric volume
        sanitizedMetricName = getSanitizedMetricName(metricName, "average");
        value = sanitizeDouble(gaugeMetric.getAverage());
        outputStream.write(getMetricLines(sanitizedMetricName, dimensions, value, timestamp));

        sanitizedMetricName = getSanitizedMetricName(metricName, "max");
        value = sanitizeDouble(gaugeMetric.getMax());
        outputStream.write(getMetricLines(sanitizedMetricName, dimensions, value, timestamp));
         */
    }

    private static byte[] getMetricLines(String metricName, String dimensions, Number value, long timestamp) {
        return (String.format(HELP_LINE, metricName, metricName) +
                String.format(METRIC_LINE, metricName, dimensions, value, timestamp)).getBytes();
    }

    private static String getSanitizedMetricName(String metricName, String suffix) {
        return sanitize(metricName) + "_" + suffix;
    }

    private static String sanitize(String name) {
        return name.replaceAll("([-.])", "_");
    }

}
