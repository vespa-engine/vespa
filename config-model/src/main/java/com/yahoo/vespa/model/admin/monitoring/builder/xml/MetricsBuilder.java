// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import static com.yahoo.vespa.model.admin.monitoring.DefaultMetricsConsumer.VESPA_CONSUMER_ID;
import static com.yahoo.vespa.model.admin.monitoring.SystemMetrics.systemMetricSet;

/**
 * @author gjoranv
 */
public class MetricsBuilder {

    private static final String ID_ATTRIBUTE = "id";

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
            metrics.addConsumer(new MetricsConsumer(consumerId, metricSet));
        }
        return metrics;
    }

    private MetricSet buildMetricSet(String consumerId, Element consumerElement) {
        List<Metric> metrics = XML.getChildren(consumerElement, "metric").stream()
                .map(metricElement -> new Metric(metricElement.getAttribute(ID_ATTRIBUTE)))
                .collect(Collectors.toCollection(LinkedList::new));

        List<MetricSet> metricSets = XML.getChildren(consumerElement, "metric-set").stream()
                .map(metricSetElement -> availableMetricSets.get(metricSetElement.getAttribute(ID_ATTRIBUTE)))
                .collect(Collectors.toCollection(LinkedList::new));

        metricSets.add(systemMetricSet);

        return new MetricSet(metricSetId(consumerId), metrics, metricSets);
    }

    private static String metricSetId(String consumerName) {
        return "user-metrics-" + consumerName;
    }

    private void throwIfIllegalConsumerId(Metrics metrics, String consumerId) {
        if (consumerId.equalsIgnoreCase(VESPA_CONSUMER_ID) && applicationType != ApplicationType.HOSTED_INFRASTRUCTURE)
            throw new IllegalArgumentException("'Vespa' is not allowed as metrics consumer id (case is ignored.)");
        if (metrics.hasConsumerIgnoreCase(consumerId))
            throw new IllegalArgumentException("'" + consumerId + "' is used as id for two metrics consumers (case is ignored.)");
    }

}
