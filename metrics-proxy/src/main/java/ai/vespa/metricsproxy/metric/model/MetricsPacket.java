// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import ai.vespa.metricsproxy.metric.Metric;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.joining;

/**
 * Represents a packet of metrics (with meta information) that belong together because they:
 * <ul>
 *     <li>share both the same dimensions and consumers, AND</li>
 *     <li>represent the same source, e.g. a vespa service or the system hardware.</li>
 * </ul>
 *
 * @author gjoranv
 */
public class MetricsPacket {

    public final int statusCode;
    public final String statusMessage;
    public final long timestamp;
    public final ServiceId service;
    private final Map<MetricId, Number> metrics;
    private final Map<DimensionId, String> dimensions;
    private final Set<ConsumerId> consumers;

    private MetricsPacket(int statusCode, String statusMessage, long timestamp, ServiceId service,
                          Map<MetricId, Number> metrics, Map<DimensionId, String> dimensions, Set<ConsumerId> consumers ) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.timestamp = timestamp;
        this.service = service;
        this.metrics = metrics;
        this.dimensions = dimensions;
        this.consumers = Set.copyOf(consumers);
    }

    public Map<MetricId, Number> metrics() { return unmodifiableMap(metrics); }
    public Map<DimensionId, String> dimensions() { return unmodifiableMap(dimensions); }
    public Set<ConsumerId> consumers() { return consumers;}

    @Override
    public String toString() {
        return "MetricsPacket{" +
                "statusCode=" + statusCode +
                ", statusMessage='" + statusMessage + '\'' +
                ", timestamp=" + timestamp +
                ", service=" + service.id +
                ", metrics=" + idMapToString(metrics, id -> id.id) +
                ", dimensions=" + idMapToString(dimensions, id -> id.id) +
                ", consumers=" + consumers.stream().map(id -> id.id).collect(joining(",", "[", "]")) +
                '}';
    }

    private static <K,V> String idMapToString(Map<K,V> map, Function<K, String> idMapper) {
        return map.entrySet().stream()
                .map(entry -> idMapper.apply(entry.getKey()) + "=" + entry.getValue())
                .collect(joining(",", "{", "}"));
    }

    public static class Builder {

        // Set defaults here, and use null guard in all setters.
        // Except for 'service' for which we require an explicit non-null value.
        private ServiceId service;
        private int statusCode = 0;
        private String statusMessage = "";
        private long timestamp = 0L;
        private Map<MetricId, Number> metrics = new LinkedHashMap<>();
        private final Map<DimensionId, String> dimensions = new LinkedHashMap<>();
        private Set<ConsumerId> consumers = Collections.emptySet();

        public Builder(ServiceId service) {
            Objects.requireNonNull(service, "Service cannot be null.");
            this.service = service;
        }

        public Builder service(ServiceId service) {
            if (service == null) throw new IllegalArgumentException("Service cannot be null.");
            this.service = service;
            return this;
        }

        public Builder statusCode(Integer statusCode) {
            if (statusCode != null) this.statusCode = statusCode;
            return this;
        }

        public Builder statusMessage(String statusMessage) {
            if (statusMessage != null) this.statusMessage = statusMessage;
            return this;
        }

        public Builder timestamp(Long timestamp) {
            if (timestamp != null) this.timestamp = timestamp;
            return this;
        }

        public Builder putMetrics(Collection<Metric> extraMetrics) {
            if (extraMetrics != null)
                extraMetrics.forEach(metric -> metrics.put(metric.getName(), metric.getValue()));
            return this;
        }

        public Builder putMetric(MetricId id, Number value) {
            metrics.put(id, value);
            return this;
        }

        public Builder retainMetrics(Set<MetricId> idsToRetain) {
            metrics.keySet().retainAll(idsToRetain);
            return this;
        }

        public Builder applyOutputNames(Map<MetricId, List<MetricId>> outputNamesById) {
            Map<MetricId, Number> newMetrics = new LinkedHashMap<>();
            outputNamesById.forEach((id, outputNames) -> {
                if (metrics.containsKey(id))
                    outputNames.forEach(outputName -> newMetrics.put(outputName, metrics.get(id)));
            });
            metrics = newMetrics;
            return this;
        }

        public Builder putDimension(DimensionId id, String value) {
            dimensions.put(id, value);
            return this;
        }

        public Builder putDimensions(Map<DimensionId, String> extraDimensions) {
            if (extraDimensions != null) dimensions.putAll(extraDimensions);
            return this;
        }

        public Builder putDimensionsIfAbsent(Map<DimensionId, String> extraDimensions) {
            if (extraDimensions != null) extraDimensions.forEach(dimensions::putIfAbsent);
            return this;
        }

        /**
         * Returns a modifiable copy of the dimension IDs of this builder, usually for use with {@link #retainDimensions(Collection)}.
         */
        public Set<DimensionId> getDimensionIds() {
            return new LinkedHashSet<>(dimensions.keySet());
        }

        public String getDimensionValue(DimensionId id) {
            return dimensions.get(id);
        }

        public Builder retainDimensions(Collection<DimensionId> idsToRetain) {
            dimensions.keySet().retainAll(idsToRetain);
            return this;
        }

        public Builder addConsumers(Set<ConsumerId> extraConsumers) {
            if ((extraConsumers != null) && !extraConsumers.isEmpty()) {
                if (consumers.isEmpty()) {
                    if (extraConsumers.size() == 1) {
                        consumers = Collections.singleton(extraConsumers.iterator().next());
                        return this;
                    }
                    consumers = new LinkedHashSet<>(extraConsumers.size());
                } else if (consumers.size() == 1) {
                    var copy = new LinkedHashSet<ConsumerId>(extraConsumers.size() + 1);
                    copy.addAll(consumers);
                    consumers = copy;
                }
                consumers.addAll(extraConsumers);
            }
            return this;
        }

        public boolean hasConsumer(ConsumerId id) {
            return consumers.contains(id);
        }

        public MetricsPacket build() {
            return new MetricsPacket(statusCode, statusMessage, timestamp, service, metrics, dimensions, consumers);
        }

        public boolean hasMetrics() {
            return ! metrics.isEmpty();
        }

        public Instant getTimestamp() { return Instant.ofEpochSecond(timestamp); }

    }

}
