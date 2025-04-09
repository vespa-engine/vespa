// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.prometheus;

import ai.vespa.metricsproxy.metric.dimensions.ApplicationDimensions;
import ai.vespa.metricsproxy.metric.dimensions.NodeDimensions;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author yj-jtakagi
 * @author gjoranv
 */
public class PrometheusUtil {

    public static String sanitize(String name) {
        String sanitized = Collector.sanitizeMetricName(name);
        return name.equals(sanitized) ? name : sanitized;
    }

    public static PrometheusModel toPrometheusModel(List<MetricsPacket> metricsPackets,
                                                    ApplicationDimensions applicationDimensions,
                                                    NodeDimensions nodeDimensions) {
        Set<MetricId> metricNames = new HashSet<>();
        for (MetricsPacket metricsPacket : metricsPackets) {
            metricNames.addAll(metricsPacket.metrics().keySet());
        }

        Map<ServiceId, List<MetricsPacket>> packetsByService = metricsPackets.stream()
                .collect(Collectors.groupingBy(MetricsPacket::service));

        var labelKeys = new ArrayList<String>();
        var labelValues = new ArrayList<String>();

        Stream.concat(applicationDimensions.getDimensions().entrySet().stream(), nodeDimensions.getDimensions().entrySet().stream())
                .forEach(entry -> {
                    labelKeys.add(entry.getKey().getIdForPrometheus());
                    labelValues.add(entry.getValue());
                });

        List<MetricFamilySamples> statusMetrics = new ArrayList<>(packetsByService.size());
        packetsByService.forEach(((serviceId, packets) -> {
            var serviceName = serviceId.getIdForPrometheus();
            if (!packets.isEmpty()) {
                var firstPacket = packets.get(0);
                var statusMetricName = serviceName + "_status";
                // MetricsPacket status 0 means OK, but it's the opposite in Prometheus.
                var statusMetricValue = (firstPacket.statusCode() == 0) ? 1 : 0;
                var sampleList = List.of(new Collector.MetricFamilySamples.Sample(statusMetricName, labelKeys, labelValues,
                        statusMetricValue, firstPacket.timestamp().toEpochMilli()));
                statusMetrics.add(new Collector.MetricFamilySamples(statusMetricName, Collector.Type.UNKNOWN, "status of service", sampleList));
            }
        }));
        return new PrometheusModel(metricNames, packetsByService, statusMetrics);
    }

}
