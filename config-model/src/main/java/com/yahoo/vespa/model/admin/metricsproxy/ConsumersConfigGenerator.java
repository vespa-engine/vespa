// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.ConsumersConfig.Consumer;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricSet;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class to generate config for metrics consumers.
 *
 * @author gjoranv
 */
class ConsumersConfigGenerator {

    /**
     * @param userConsumers the consumers set up by the user in services.xml
     * @return a list of consumer builders (a mapping from consumer to its metrics)
     */
    static List<Consumer.Builder> generateConsumers(MetricsConsumer defaultConsumer,
                                                    Map<String, MetricsConsumer> userConsumers,
                                                    SystemName systemName) {
        // Normally, the user given consumers should not contain VESPA_CONSUMER_ID,
        // but it's allowed for some internally used applications.
        var allConsumers = new LinkedHashMap<>(userConsumers);
        allConsumers.put(MetricsConsumer.vespa.id(),
                         combineConsumers(defaultConsumer, allConsumers.get(MetricsConsumer.vespa.id())));
        allConsumers.put(MetricsConsumer.autoscaling.id(), MetricsConsumer.autoscaling);

        if (systemName.isPublic())
            allConsumers.put(MetricsConsumer.vespaCloud.id(), MetricsConsumer.vespaCloud);

        return allConsumers.values().stream()
                .map(ConsumersConfigGenerator::toConsumerBuilder)
                .collect(Collectors.toList());
    }

    /*
     * Returns a new consumer that is a combination of the two given consumers
     * (ignoring the id of the consumers' metric sets).
     * If a metric with the same id exists in both consumers, output name and
     * dimensions from the 'overriding' consumer is used, but dimensions from 'original'
     * are added if they don't exist in 'overriding'.
     */
    private static MetricsConsumer combineConsumers(MetricsConsumer original, MetricsConsumer overriding) {
        if (overriding == null) return original;
        return addMetrics(original, overriding.metrics());
    }

    static MetricsConsumer addMetrics(MetricsConsumer original, Map<String, Metric> metrics) {
        if (metrics == null) return original;

        Map<String, Metric> combinedMetrics = new LinkedHashMap<>(original.metrics());
        metrics.forEach((name, newMetric) ->
                                combinedMetrics.put(name, combineMetrics(original.metrics().get(name), newMetric)));

        return new MetricsConsumer(original.id(),
                                   new MetricSet(original.metricSet().getId(), combinedMetrics.values()));
    }

    private static Metric combineMetrics(Metric original, Metric newMetric) {
        return original != null ? newMetric.addDimensionsFrom(original) : newMetric;
    }

    static Consumer.Builder toConsumerBuilder(MetricsConsumer consumer) {
        Consumer.Builder builder = new Consumer.Builder().name(consumer.id());
        consumer.metrics().values().stream().sorted(Comparator.comparing(a -> a.name)).forEach(metric -> builder.metric(toConsumerMetricBuilder(metric)));
        return builder;
    }

    private static Consumer.Metric.Builder toConsumerMetricBuilder(Metric metric) {
        Consumer.Metric.Builder builder = new Consumer.Metric.Builder().name(metric.name)
                .outputname(metric.outputName)
                .description(metric.description);
        metric.dimensions.forEach((name, value) -> builder.dimension(toMetricDimensionBuilder(name, value)));
        return builder;
    }

    private static Consumer.Metric.Dimension.Builder toMetricDimensionBuilder(String name, String value) {
        return new Consumer.Metric.Dimension.Builder()
                .key(name)
                .value(value);
    }

}
