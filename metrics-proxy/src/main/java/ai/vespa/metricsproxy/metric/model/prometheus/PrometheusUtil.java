// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.prometheus;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * @author yj-jtakagi
 * @author gjoranv
 */
public class PrometheusUtil {

    public static PrometheusModel toPrometheusModel(List<MetricsPacket> metricsPackets) {
        Map<ServiceId, List<MetricsPacket>> packetsByService = metricsPackets.stream()
                .collect(Collectors.groupingBy(packet -> packet.service));

        List<MetricFamilySamples> metricFamilySamples = new ArrayList<>(packetsByService.size());

        Map<String, List<Sample>> samples = new HashMap<>();
        packetsByService.forEach(((serviceId, packets) -> {

            var serviceName = Collector.sanitizeMetricName(serviceId.id);
            for (var packet : packets) {
                var dimensions = packet.dimensions();
                List<String> labels = new ArrayList<>(dimensions.size());
                List<String> labelValues = new ArrayList<>(dimensions.size());
                for (var entry : dimensions.entrySet()) {
                    var labelName = Collector.sanitizeMetricName(entry.getKey().id);
                    labels.add(labelName);
                    labelValues.add(entry.getValue());
                }
                labels.add("vespa_service");
                labelValues.add(serviceName);

                for (var metric : packet.metrics().entrySet()) {
                    var metricName = Collector.sanitizeMetricName(metric.getKey().id);
                    List<Sample> sampleList;
                    if (samples.containsKey(metricName)) {
                        sampleList = samples.get(metricName);
                    } else {
                        sampleList = new ArrayList<>();
                        samples.put(metricName, sampleList);
                        metricFamilySamples.add(new MetricFamilySamples(metricName, Collector.Type.UNTYPED, "", sampleList));
                    }
                    sampleList.add(new Sample(metricName, labels, labelValues, metric.getValue().doubleValue(), packet.timestamp * 1000));
                }
            }
        }));

        return new PrometheusModel(metricFamilySamples);
    }

}
