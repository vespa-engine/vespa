// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.prometheus;

import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import io.prometheus.client.Collector;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author yj-jtakagi
 * @author gjoranv
 */
public class PrometheusModel implements Enumeration<Collector.MetricFamilySamples> {
    private final Map<ServiceId, List<MetricsPacket>> packetsByServiceId;
    private final Iterator<MetricId> metricIterator;
    private final Iterator<Collector.MetricFamilySamples> statusMetrics;

    PrometheusModel(Set<MetricId> metricNames, Map<ServiceId, List<MetricsPacket>> packetsByServiceId,
                    List<Collector.MetricFamilySamples> statusMetrics) {
        metricIterator = metricNames.iterator();
        this.packetsByServiceId = packetsByServiceId;
        this.statusMetrics = statusMetrics.iterator();
    }

    @Override
    public boolean hasMoreElements() {
        return metricIterator.hasNext() || statusMetrics.hasNext();
    }

    @Override
    public Collector.MetricFamilySamples nextElement() {
        return metricIterator.hasNext()
                ? createMetricFamily(metricIterator.next())
                : statusMetrics.next();
    }

    public String serialize() {
        var writer = new StringWriter();
        try {
            TextFormat.write004(writer, this);
        } catch (Exception e) {
            throw new PrometheusRenderingException("Could not render metrics. Check the log for details.", e);
        }
        return writer.toString();
    }

    private Collector.MetricFamilySamples createMetricFamily(MetricId metricId) {
        List<Collector.MetricFamilySamples.Sample> sampleList = new ArrayList<>();
        var samples = new Collector.MetricFamilySamples(metricId.getIdForPrometheus(), Collector.Type.UNKNOWN, "", sampleList);
        packetsByServiceId.forEach(((serviceId, packets) -> {
            var serviceName = serviceId.getIdForPrometheus();
            for (var packet : packets) {
                Long timeStamp = packet.timestamp * 1000;
                var dimensions = packet.dimensions();
                List<String> labels = new ArrayList<>(dimensions.size());
                List<String> labelValues = new ArrayList<>(dimensions.size());
                for (var entry : dimensions.entrySet()) {
                    var labelName = entry.getKey().getIdForPrometheus();
                    labels.add(labelName);
                    labelValues.add(entry.getValue());
                }
                labels.add("vespa_service");
                labelValues.add(serviceName);

                Number metric = packet.metrics().get(metricId);
                if (metric != null) {
                    sampleList.add(new Collector.MetricFamilySamples.Sample(metricId.getIdForPrometheus(), labels, labelValues, metric.doubleValue(), timeStamp));
                }
            }
        }));
        return samples;
    }

}
