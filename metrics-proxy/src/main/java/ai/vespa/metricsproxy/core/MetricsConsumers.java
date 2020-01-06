// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.metricsproxy.core;

import ai.vespa.metricsproxy.core.ConsumersConfig.Consumer;
import ai.vespa.metricsproxy.metric.model.ConsumerId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static com.yahoo.stream.CustomCollectors.toLinkedMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.collectingAndThen;

/**
 * Contains metrics consumers and their metrics, and mappings between these.
 * All collections are final and immutable.
 *
 * @author gjoranv
 */
public class MetricsConsumers {

    // All metrics for each consumer.
    private final Map<ConsumerId, List<Consumer.Metric>> consumerMetrics;

    // All consumers for each metric (more useful than the opposite map).
    private final Map<Consumer.Metric, List<ConsumerId>> consumersByMetric;

    public MetricsConsumers(ConsumersConfig config) {
        consumerMetrics = config.consumer().stream().collect(
                toUnmodifiableLinkedMap(consumer -> toConsumerId(consumer.name()), Consumer::metric));

        consumersByMetric = createConsumersByMetric(consumerMetrics);
    }

    /**
     * @param consumer The consumer
     * @return The metrics for the given consumer.
     */
    public List<Consumer.Metric> getMetricDefinitions(ConsumerId consumer) {
        return consumerMetrics.get(consumer);
    }

    public Map<Consumer.Metric, List<ConsumerId>> getConsumersByMetric() {
        return consumersByMetric;
    }

    public Set<ConsumerId> getAllConsumers() {
        return unmodifiableSet(consumerMetrics.keySet());
    }

    /**
     * Helper function to create mapping from metric to consumers.
     * TODO: consider reversing the mapping in metrics-consumers.def instead: metric{}.consumer[]
     */
    private static Map<Consumer.Metric, List<ConsumerId>>
    createConsumersByMetric(Map<ConsumerId, List<Consumer.Metric>> metricsByConsumer) {
        Map<Consumer.Metric, List<ConsumerId>> consumersByMetric = new LinkedHashMap<>();
        metricsByConsumer.forEach(
                (consumer, metrics) -> metrics.forEach(
                        metric -> consumersByMetric.computeIfAbsent(metric, unused -> new ArrayList<>())
                                .add(consumer)));
        return Collections.unmodifiableMap(consumersByMetric);
    }

    public static <T, K, U> Collector<T, ?, Map<K, U>> toUnmodifiableLinkedMap(Function<? super T, ? extends K> keyMapper,
                                                                                Function<? super T, ? extends U> valueMapper) {
        return collectingAndThen(toLinkedMap(keyMapper, valueMapper), Collections::unmodifiableMap);
    }

}
