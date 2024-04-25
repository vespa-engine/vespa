// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.prometheus;

import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
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
public class PrometheusModel implements Enumeration<MetricFamilySamples> {
    private final Map<ServiceId, List<MetricsPacket>> packetsByServiceId;
    private final Iterator<MetricId> metricIterator;
    private final Iterator<MetricFamilySamples> statusMetrics;

    PrometheusModel(Set<MetricId> metricNames, Map<ServiceId,
                    List<MetricsPacket>> packetsByServiceId,
                    List<MetricFamilySamples> statusMetrics)
    {
        metricIterator = metricNames.iterator();
        this.packetsByServiceId = packetsByServiceId;
        this.statusMetrics = statusMetrics.iterator();
    }

    @Override
    public boolean hasMoreElements() {
        return metricIterator.hasNext() || statusMetrics.hasNext();
    }

    @Override
    public MetricFamilySamples nextElement() {
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

    private MetricFamilySamples createMetricFamily(MetricId metricId) {
        List<MetricFamilySamples.Sample> sampleList = new ArrayList<>();
        packetsByServiceId.forEach(((serviceId, packets) -> {
            for (var packet : packets) {
                Number metric = packet.metrics().get(metricId);
                if (metric != null) {
                    sampleList.add(createSample(serviceId, metricId, metric, packet.timestamp().toEpochMilli(), packet.dimensions()));
                }
            }
        }));
        return new MetricFamilySamples(metricId.getIdForPrometheus(), Collector.Type.UNKNOWN, "", sampleList);
    }
    private static Sample createSample(ServiceId serviceId, MetricId metricId, Number metric,
                                       Long timeStamp, Map<DimensionId, String> dimensions)
    {
        List<String> labels = new ArrayList<>(dimensions.size());
        List<String> labelValues = new ArrayList<>(dimensions.size());
        for (var entry : dimensions.entrySet()) {
            var labelName = entry.getKey().getIdForPrometheus();
            labels.add(labelName);
            labelValues.add(entry.getValue());
        }
        labels.add("vespa_service");
        labelValues.add(serviceId.getIdForPrometheus());
        return new Sample(metricId.getIdForPrometheus(), labels, labelValues, metric.doubleValue(), timeStamp);
    }

}
