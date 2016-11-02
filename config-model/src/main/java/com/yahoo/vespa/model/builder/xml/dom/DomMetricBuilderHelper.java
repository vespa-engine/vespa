// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.text.XML;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import org.w3c.dom.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for parsing yamasmetric config.
 *
 * @author trygve
 * @since 5.1
 */
public class DomMetricBuilderHelper {


    /**
     * Build metricConsumer config
     *
     * @param spec xml element
     * @return  a map from metric name to a {@link MetricsConsumer}
     */
    protected static Map<String, MetricsConsumer> buildMetricsConsumers(Element spec) {
        Map<String, MetricsConsumer> metricsConsumers = new LinkedHashMap<>();
        List<Element> consumersElem = XML.getChildren(spec, "consumer");
        for (Element consumer : consumersElem) {
            String consumerName = consumer.getAttribute("name");
            Map<String, Metric> metrics = new LinkedHashMap<>();
            List<Element> metricsEl = XML.getChildren(consumer, "metric");
            if (metricsEl != null) {
                for (Element metric : metricsEl) {
                    String metricName = metric.getAttribute("name");
                    String outputName = metric.getAttribute("output-name");
                    metrics.put(metricName, new Metric(metricName, outputName));
                }
            }
            MetricsConsumer metricsConsumer = new MetricsConsumer(consumerName, metrics);
            metricsConsumers.put(consumerName, metricsConsumer);
        }
        return metricsConsumers;
    }
}
