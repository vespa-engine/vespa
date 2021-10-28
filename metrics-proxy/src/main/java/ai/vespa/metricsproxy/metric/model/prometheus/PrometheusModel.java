// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author yj-jtakagi
 * @author gjoranv
 */
public class PrometheusModel implements Enumeration<Collector.MetricFamilySamples> {
    private static Logger log = Logger.getLogger(PrometheusModel.class.getName());

    private final Iterator<Collector.MetricFamilySamples> metricFamilySamplesIterator;

    PrometheusModel(List<Collector.MetricFamilySamples> metricFamilySamples) {
        this.metricFamilySamplesIterator = metricFamilySamples.iterator();
    }

    @Override
    public boolean hasMoreElements() {
        return metricFamilySamplesIterator.hasNext();
    }

    @Override
    public Collector.MetricFamilySamples nextElement() {
        return metricFamilySamplesIterator.next();
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

}
