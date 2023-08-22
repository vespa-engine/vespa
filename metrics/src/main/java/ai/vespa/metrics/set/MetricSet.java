// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics.set;

import ai.vespa.metrics.Suffix;
import ai.vespa.metrics.VespaMetrics;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;

/**
 * Models a metric set containing a set of metrics and child metric sets.
 * This should be immutable.
 *
 * @author gjoranv
 */
public class MetricSet {

    private final String id;
    private final Map<String, Metric> metrics;
    private final Set<MetricSet> children;

    public MetricSet(String id, Collection<Metric> metrics) {
        this(id, metrics, Set.of());
    }

    public MetricSet(String id, Collection<Metric> metrics, Collection<MetricSet> children) {
        this.id = Objects.requireNonNull(id, "Id cannot be null or empty.");

        this.metrics = toMapByName(metrics);
        this.children = new LinkedHashSet<>(children);
    }

    public static MetricSet empty() {
        return new MetricSet("empty", Set.of());
    }

    public final String getId() { return id; }

    /**
     * Returns all metrics in this set, including all metrics in any contained metric sets.
     *
     * Joins this set's metrics with its child sets into a named flat map of metrics.
     * In the case of duplicate metrics, the metrics directly defined in this set
     * takes precedence with respect to output name, description and dimension value
     * (even if they are empty), while new dimensions from the children will be added.
     *
     * @return all the metrics contained in this set
     */
    public final Map<String, Metric> getMetrics() {
        return unmodifiableMap(flatten(metrics, children));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetricSet that)) return false;

        return Objects.equals(id, that.id);

    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    private Map<String, Metric> flatten(Map<String, Metric> metrics, Set<MetricSet> children) {
        Map<String, Metric> joinedMetrics = new LinkedHashMap<>(metrics);

        for (MetricSet child : children) {
            child.getMetrics().forEach(
                    (name, metric) -> {
                        if (joinedMetrics.containsKey(name))
                            joinedMetrics.put(name, joinedMetrics.get(name).addDimensionsFrom(metric));
                        else
                            joinedMetrics.put(name, metric);
                    });
        }
        return joinedMetrics;
    }

    private Map<String, Metric> toMapByName(Collection<Metric> metrics) {
        Map<String, Metric> metricMap = new LinkedHashMap<>();
        metrics.forEach(metric -> metricMap.put(metric.name, metric));
        return metricMap;
    }


    public static class Builder {
        private final String id;
        private final Set<Metric> metrics = new LinkedHashSet<>();
        private final Set<MetricSet> children = new LinkedHashSet<>();

        public Builder(String id) {
            this.id = id;
        }

        public Builder metric(String metric) {
            return metric(new Metric(metric));
        }

        /** Adds all given suffixes of the given metric to this set. */
        public Builder metric(VespaMetrics metric, EnumSet<Suffix> suffixes) {
            suffixes.forEach(suffix -> metrics.add(new Metric(metric.baseName() + "." + suffix.suffix())));
            return this;
        }

        public Builder metric(Metric metric) {
            metrics.add(metric);
            return this;
        }

        public Builder metrics(Collection<Metric> metrics) {
            this.metrics.addAll(metrics);
            return this;
        }

        public Builder metricSet(MetricSet child) {
            children.add(child);
            return this;
        }

        public MetricSet build() {
            return new MetricSet(id, metrics, children);
        }
    }

}
