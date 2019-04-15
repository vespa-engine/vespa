package com.yahoo.vespa.tenant.cd.metrics;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringJoiner;

import static java.util.Map.copyOf;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * A set of statistics for a metric, for points over a String-indexed space.
 *
 * @author jonmv
 */
public class Metric {

    private final Map<Map<String, ?>, Statistic> statistics;

    private Metric(Map<Map<String, ?>, Statistic> statistics) {
        this.statistics = copyOf(statistics);
    }

    /** Creates a new Metric with a copy of the given data. */
    public static Metric of(Map<Map<String, ?>, Statistic> data) {
        if (data.isEmpty())
            throw new IllegalArgumentException("No data given.");

        Set<String> dimensions = data.keySet().iterator().next().keySet();
        for (Map<String, ?> point : data.keySet()) {
            if (point.keySet().contains(null))
                throw new IllegalArgumentException("Dimensions may not be null: '" + point.keySet() + "'.");

            if ( ! point.keySet().equals(dimensions))
                throw new IllegalArgumentException("Given data has inconsistent dimensions: '" + dimensions + "' vs '" + point.keySet() + "'.");

            if (point.values().contains(null))
                throw new IllegalArgumentException("Position along a dimension may not be null: '" + point + "'.");
        }

        return new Metric(data);
    }

    /** Returns a Metric view of the subset of points in the given hyperplane; its dimensions must be a subset of those of this Metric. */
    public Metric at(Map<String, ?> hyperplane) {
        return new Metric(statistics.keySet().stream()
                                    .filter(point -> point.entrySet().containsAll(hyperplane.entrySet()))
                                    .collect(toUnmodifiableMap(point -> point, statistics::get)));
    }

    /** If this Metric contains a single point, returns the Statistic of that point; otherwise, throws an exception. */
    public Statistic statistic() {
        if (statistics.size() == 1)
            return statistics.values().iterator().next();

        if (statistics.isEmpty())
            throw new NoSuchElementException("This Metric has no data.");

        throw new IllegalStateException("This Metric has more than one point of data.");
    }

    /** Returns the underlying, unmodifiable Map. */
    public Map<Map<String, ?>, Statistic> asMap() {
        return statistics;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Metric.class.getSimpleName() + "[", "]")
                .add("statistics=" + statistics)
                .toString();
    }

}
