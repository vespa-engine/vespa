// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import ai.vespa.metricsproxy.metric.Metric;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ObjDoubleConsumer;

import static java.util.stream.Collectors.joining;

/**
 * Represents a packet of metrics (with meta information) that belong together because they:
 * <ul>
 *     <li>share both the same dimensions and consumers, AND</li>
 *     <li>represent the same source, e.g. a vespa service or the system hardware.</li>
 * </ul>
 *
 * Metrics and dimensions are stored in parallel arrays, in insertion order, as many packets are held
 * in memory at once. Metric and dimension ids are interned, so lookups compare them by identity.
 *
 * @author gjoranv
 */
public class MetricsPacket {

    private static final MetricId[] NO_METRIC_IDS = new MetricId[0];
    private static final double[] NO_METRIC_VALUES = new double[0];
    private static final DimensionId[] NO_DIMENSION_IDS = new DimensionId[0];
    private static final String[] NO_DIMENSION_VALUES = new String[0];

    private final int statusCode;
    private final String statusMessage;
    private final ServiceId service;
    private final Instant timestamp;
    private final MetricId[] metricIds;
    private final double[] metricValues;
    private final DimensionId[] dimensionIds;
    private final String[] dimensionValues;
    private final Set<ConsumerId> consumers;

    private MetricsPacket(int statusCode, String statusMessage, Instant timestamp, ServiceId service,
                          Map<MetricId, Number> metrics, Map<DimensionId, String> dimensions, Set<ConsumerId> consumers,
                          Sharing sharing) {
        this.statusCode = statusCode;
        this.statusMessage = sharing.share(statusMessage);
        this.timestamp = sharing.share(timestamp);
        this.service = sharing.share(service);
        this.consumers = Set.copyOf(consumers);

        if (metrics.isEmpty()) {
            metricIds = NO_METRIC_IDS;
            metricValues = NO_METRIC_VALUES;
        } else {
            metricIds = new MetricId[metrics.size()];
            metricValues = new double[metrics.size()];
            int i = 0;
            for (var entry : metrics.entrySet()) {
                metricIds[i] = entry.getKey();
                metricValues[i++] = entry.getValue().doubleValue();
            }
        }
        if (dimensions.isEmpty()) {
            dimensionIds = NO_DIMENSION_IDS;
            dimensionValues = NO_DIMENSION_VALUES;
        } else {
            dimensionIds = new DimensionId[dimensions.size()];
            dimensionValues = new String[dimensions.size()];
            int i = 0;
            for (var entry : dimensions.entrySet()) {
                dimensionIds[i] = entry.getKey();
                dimensionValues[i++] = sharing.share(entry.getValue());
            }
        }
    }

    public int metricCount() { return metricIds.length; }
    public MetricId metricId(int index) { return metricIds[index]; }
    public double metricValue(int index) { return metricValues[index]; }

    /** Returns the index of the given metric, or -1 if this packet does not have it. */
    public int metricIndex(MetricId id) {
        for (int i = 0; i < metricIds.length; i++)
            if (metricIds[i] == id) return i;
        return -1;
    }

    public boolean hasMetric(MetricId id) { return metricIndex(id) >= 0; }

    public void forEachMetric(ObjDoubleConsumer<MetricId> action) {
        for (int i = 0; i < metricIds.length; i++)
            action.accept(metricIds[i], metricValues[i]);
    }

    public int dimensionCount() { return dimensionIds.length; }
    public DimensionId dimensionId(int index) { return dimensionIds[index]; }
    public String dimensionValue(int index) { return dimensionValues[index]; }

    /** Returns the value of the given dimension, or null if this packet does not have it. */
    public String dimension(DimensionId id) {
        for (int i = 0; i < dimensionIds.length; i++)
            if (dimensionIds[i] == id) return dimensionValues[i];
        return null;
    }

    public void forEachDimension(BiConsumer<DimensionId, String> action) {
        for (int i = 0; i < dimensionIds.length; i++)
            action.accept(dimensionIds[i], dimensionValues[i]);
    }

    public Set<ConsumerId> consumers() { return consumers; }
    public Instant timestamp() { return timestamp; }
    public ServiceId service() { return service; }
    public int statusCode() { return statusCode; }
    public String statusMessage() { return statusMessage; }

    @Override
    public String toString() {
        return "MetricsPacket{" +
                "statusCode=" + statusCode +
                ", statusMessage='" + statusMessage + '\'' +
                ", timestamp=" + timestamp +
                ", service=" + service.id +
                ", metrics=" + idArrayToString(metricIds, id -> id.id, i -> metricValues[i]) +
                ", dimensions=" + idArrayToString(dimensionIds, id -> id.id, i -> dimensionValues[i]) +
                ", consumers=" + consumers.stream().map(id -> id.id).collect(joining(",", "[", "]")) +
                '}';
    }

    private static <K> String idArrayToString(K[] ids, Function<K, String> idMapper, IntFunction<Object> value) {
        var joiner = new StringJoiner(",", "{", "}");
        for (int i = 0; i < ids.length; i++)
            joiner.add(idMapper.apply(ids[i]) + "=" + value.apply(i));
        return joiner.toString();
    }

    /**
     * Makes packets built with the same instance share equal dimension values, service ids, timestamps and
     * status messages, as packets from one source repeat these (e.g. the hostname in every packet from a node).
     * Scope an instance to one batch of packets, such as one node's response, and discard it afterwards,
     * so it holds no values beyond the packets themselves. Not thread safe.
     */
    public static final class Sharing {

        private static final Sharing NONE = new Sharing(false);

        private final Map<Object, Object> instances;

        public Sharing() { this(true); }

        private Sharing(boolean enabled) {
            instances = enabled ? new HashMap<>() : null;
        }

        @SuppressWarnings("unchecked")
        <T> T share(T value) {
            if (instances == null || value == null) return value;
            Object existing = instances.putIfAbsent(value, value);
            return existing == null ? value : (T) existing;
        }

    }

    public static class Builder {

        // Set defaults here, and use null guard in all setters.
        // Except for 'service' for which we require an explicit non-null value.
        private ServiceId service;
        private int statusCode = 0;
        private String statusMessage = "";
        private Instant timestamp = Instant.EPOCH;
        private Map<MetricId, Number> metrics = new LinkedHashMap<>();
        private final Map<DimensionId, String> dimensions = new LinkedHashMap<>();
        private Set<ConsumerId> consumers = Set.of();

        public Builder(ServiceId service) {
            Objects.requireNonNull(service, "Service cannot be null.");
            this.service = service;
        }

        public Builder service(ServiceId service) {
            if (service == null) throw new IllegalArgumentException("Service cannot be null.");
            this.service = service;
            return this;
        }

        public ServiceId getServiceId() { return service; }

        public Builder statusCode(Integer statusCode) {
            if (statusCode != null) this.statusCode = statusCode;
            return this;
        }

        public Builder statusMessage(String statusMessage) {
            if (statusMessage != null) this.statusMessage = statusMessage;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            if (timestamp != null) this.timestamp = timestamp;
            return this;
        }

        public Builder putMetrics(Collection<Metric> extraMetrics) {
            if (extraMetrics != null)
                extraMetrics.forEach(metric -> putMetric(metric.getName(), metric.getValue()));
            return this;
        }

        public Builder putMetric(MetricId id, Number value) {
            metrics.put(id, Objects.requireNonNull(value, () -> "No value for metric " + id));
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
                        consumers = Set.of(extraConsumers.iterator().next());
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
            return build(Sharing.NONE);
        }

        /** Builds a packet which shares equal values with other packets built with the given sharing. */
        public MetricsPacket build(Sharing sharing) {
            return new MetricsPacket(statusCode, statusMessage, timestamp, service, metrics, dimensions, consumers, sharing);
        }

        public boolean hasMetrics() {
            return ! metrics.isEmpty();
        }

        public Instant getTimestamp() { return timestamp; }

    }

}
