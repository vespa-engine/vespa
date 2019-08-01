package ai.vespa.metricsproxy.metric.model.prometheus;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import io.prometheus.client.Collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PrometheusUtil {
    private static final Pattern SANITIZE_PREFIX_PATTERN = Pattern.compile("^[^a-zA-Z_]");
    private static final Pattern SANITIZE_BODY_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");

    public static PrometheusModel toPrometheusModel(List<MetricsPacket> metricsPackets) {
        Map<ServiceId, List<MetricsPacket>> packetsByService = metricsPackets.stream()
            .collect(Collectors.groupingBy(packet -> packet.service));

        List<Collector.MetricFamilySamples> metricFamilySamples = new ArrayList<>(packetsByService.size());

        packetsByService.forEach(((serviceId, packets) -> {
            Map<String, List<Collector.MetricFamilySamples.Sample>> samples = new HashMap<>();
            
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

                for (var metric : packet.metrics().entrySet()) {
                    var metricName = serviceName + "_" + Collector.sanitizeMetricName(metric.getKey().id);
                    List<Collector.MetricFamilySamples.Sample> sampleList;
                    if (samples.containsKey(metricName)) {
                        sampleList = samples.get(metricName);
                    } else {
                        sampleList = new ArrayList<>();
                        samples.put(metricName, sampleList);
                        metricFamilySamples.add(new Collector.MetricFamilySamples(metricName, Collector.Type.UNTYPED, "", sampleList));
                    }
                    sampleList.add(new Collector.MetricFamilySamples.Sample(metricName, labels, labelValues, metric.getValue().doubleValue(), packet.timestamp * 1000));
                }
            }
            // convert status message to 0,1 metric
            var firstPacket = packets.get(0);
            String statusMetricName = serviceName + "_status";
            double statusMetricValue = "up".equals(firstPacket.statusCode) ? 1.0 : 0.0;
            List<Collector.MetricFamilySamples.Sample> sampleList = Collections.singletonList(new Collector.MetricFamilySamples.Sample(statusMetricName, Collections.emptyList(), Collections.emptyList(), statusMetricValue, firstPacket.timestamp * 1000));
            metricFamilySamples.add(new Collector.MetricFamilySamples(statusMetricName, Collector.Type.UNTYPED, "status of service", sampleList));
        }));

        return new PrometheusModel(metricFamilySamples);
    }

    public static String sanitizeMetricLabelName(String labelName) {
        // same as Collector.sanitizeMetricName...
        return SANITIZE_BODY_PATTERN.matcher(SANITIZE_PREFIX_PATTERN.matcher(labelName).replaceFirst("_")).replaceAll("_");
    }
}
