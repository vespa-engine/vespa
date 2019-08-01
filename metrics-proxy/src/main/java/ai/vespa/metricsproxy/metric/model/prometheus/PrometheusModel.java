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
        } catch (IOException e) {
            log.log(Level.WARNING, "Got exception when rendering metrics:", e);
            throw new PrometheusRenderingException("Could not render metrics. Check the log for details.");
        }
        return writer.toString();
    }
}
