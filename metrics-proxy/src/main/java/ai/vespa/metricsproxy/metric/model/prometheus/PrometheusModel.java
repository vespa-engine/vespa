// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.prometheus;

import ai.vespa.metricsproxy.metric.model.MetricId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.ServiceId;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.io.Writer;
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

    PrometheusModel(Set<MetricId> metricNames,
                    Map<ServiceId, List<MetricsPacket>> packetsByServiceId,
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
        serialize(writer);
        return writer.toString();
    }

    public void serialize(Writer writer) {
        try {
            // TextFormat writes many small strings, which are slow to encode one by one
            var buffered = new BufferedWriter(writer, 1 << 16);
            TextFormat.write004(buffered, this);
            buffered.flush();
        } catch (Exception e) {
            throw new PrometheusRenderingException("Could not render metrics. Check the log for details.", e);
        }
    }

    private MetricFamilySamples createMetricFamily(MetricId metricId) {
        List<MetricFamilySamples.Sample> sampleList = new ArrayList<>();
        packetsByServiceId.forEach(((serviceId, packets) -> {
            for (var packet : packets) {
                int index = packet.metricIndex(metricId);
                if (index >= 0) {
                    sampleList.add(createSample(serviceId, metricId, packet.metricValue(index), packet.timestamp().toEpochMilli(), packet));
                }
            }
        }));
        return new MetricFamilySamples(metricId.getIdForPrometheus(), Collector.Type.UNKNOWN, "", sampleList);
    }

    private static Sample createSample(ServiceId serviceId, MetricId metricId, double value,
                                       long timeStamp, MetricsPacket packet)
    {
        int dimensionCount = packet.dimensionCount();
        List<String> labels = new ArrayList<>(dimensionCount + 1);
        List<String> labelValues = new ArrayList<>(dimensionCount + 1);
        for (int i = 0; i < dimensionCount; i++) {
            labels.add(packet.dimensionId(i).getIdForPrometheus());
            labelValues.add(packet.dimensionValue(i));
        }
        labels.add("vespa_service");
        labelValues.add(serviceId.getIdForPrometheus());
        return new Sample(metricId.getIdForPrometheus(), labels, labelValues, value, timeStamp);
    }

}
