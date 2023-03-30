// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring.builder.xml;

import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricSet;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.builder.Metrics;
import org.w3c.dom.Element;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;

/**
 * @author gjoranv
 */
public class MetricsBuilder {

    private static final String ID_ATTRIBUTE = "id";
    private static final String DISPLAY_NAME_ATTRIBUTE = "display-name";

    private final ApplicationType applicationType;
    private final Map<String, MetricSet> availableMetricSets;

    public MetricsBuilder(ApplicationType applicationType, Map<String, MetricSet> availableMetricSets) {
        this.applicationType = applicationType;
        this.availableMetricSets = availableMetricSets;
    }

    public Metrics buildMetrics(Element metricsElement) {
        Metrics metrics = new Metrics();
        for (Element consumerElement : XML.getChildren(metricsElement, "consumer")) {
            String consumerId = consumerElement.getAttribute(ID_ATTRIBUTE);
            throwIfIllegalConsumerId(metrics, consumerId);

            MetricSet metricSet = buildMetricSet(consumerId, consumerElement);
            var consumer = new MetricsConsumer(consumerId, metricSet);
            metrics.addConsumer(consumer);
        }
        return metrics;
    }

    private static Metric metricFromElement(Element elem) {
        String m_id = elem.getAttribute(ID_ATTRIBUTE);
        String m_dn = elem.getAttribute(DISPLAY_NAME_ATTRIBUTE);
        if (m_dn == null || "".equals(m_dn)) {
            return new Metric(m_id);
        }
        return new Metric(m_id, m_dn);
    }

    private MetricSet buildMetricSet(String consumerId, Element consumerElement) {
        List<Metric> metrics = XML.getChildren(consumerElement, "metric").stream()
                .map(MetricsBuilder::metricFromElement)
                .collect(Collectors.toCollection(LinkedList::new));

        List<MetricSet> metricSets = XML.getChildren(consumerElement, "metric-set").stream()
                .map(metricSetElement -> getMetricSetOrThrow(metricSetElement.getAttribute(ID_ATTRIBUTE)))
                .collect(Collectors.toCollection(LinkedList::new));

        metricSets.add(defaultVespaMetricSet);
        metricSets.add(systemMetricSet);

        return new MetricSet(metricSetId(consumerId), metrics, metricSets);
    }

    private static String metricSetId(String consumerName) {
        return "user-metrics-" + consumerName;
    }

    private MetricSet getMetricSetOrThrow(String id) {
        if (! availableMetricSets.containsKey(id)) throw new IllegalArgumentException("No such metric-set: " + id);
        return availableMetricSets.get(id);
    }

    private void throwIfIllegalConsumerId(Metrics metrics, String consumerId) {
        if (consumerId.equalsIgnoreCase(MetricsConsumer.vespa.id()) && applicationType != ApplicationType.HOSTED_INFRASTRUCTURE)
            throw new IllegalArgumentException("'Vespa' is not allowed as metrics consumer id (case is ignored.)");

        if (consumerId.equalsIgnoreCase(MetricsConsumer.defaultConsumer.id()))
            throw new IllegalArgumentException("'" + MetricsConsumer.defaultConsumer.id() + "' is not allowed as metrics consumer id (case is ignored.)");

        if (consumerId.equalsIgnoreCase(MetricsConsumer.autoscaling.id()))
            throw new IllegalArgumentException("'" + MetricsConsumer.autoscaling.id() + " is not allowed as metrics consumer id (case is ignored.)");

        if (consumerId.equalsIgnoreCase(MetricsConsumer.vespaCloud.id()))
            throw new IllegalArgumentException("'" + MetricsConsumer.vespaCloud.id() + " is not allowed as metrics consumer id (case is ignored.)");

        if (metrics.hasConsumerIgnoreCase(consumerId))
            throw new IllegalArgumentException("'" + consumerId + "' is used as id for two metrics consumers (case is ignored.)");
    }

}
