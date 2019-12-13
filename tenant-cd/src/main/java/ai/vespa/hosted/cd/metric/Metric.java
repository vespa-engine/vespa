package ai.vespa.hosted.cd.metric;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringJoiner;

import static java.util.Map.copyOf;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * A set of statistics for a metric, for points over a space with named dimensions of arbitrary type.
 *
 * @author jonmv
 */
public class Metric {

    private final Map<Map<String, ?>, Statistic> statistics;

    private Metric(Map<Map<String, ?>, Statistic> statistics) {
        this.statistics = statistics;
    }

    /** Creates a new Metric with a copy of the given data. */
    public static Metric of(Map<Map<String, ?>, Statistic> data) {
        if (data.isEmpty())
            throw new IllegalArgumentException("No data given.");

        Map<Map<String, ?>, Statistic> copies = new HashMap<>();
        Set<String> dimensions = data.keySet().iterator().next().keySet();
        data.forEach((point, statistic) -> {
            if ( ! point.keySet().equals(dimensions))
                throw new IllegalArgumentException("Given data has inconsistent dimensions: '" + dimensions + "' vs '" + point.keySet() + "'.");

            copies.put(copyOf(point), statistic);
        });

        return new Metric(copyOf(copies));
    }

    /** Returns a Metric view of the subset of points in the given hyperplane; its dimensions must be a subset of those of this Metric. */
    public Metric at(Map<String, ?> hyperplane) {
        return new Metric(statistics.keySet().stream()
                                    .filter(point -> point.entrySet().containsAll(hyperplane.entrySet()))
                                    .collect(toUnmodifiableMap(point -> point, statistics::get)));
    }

    /** Returns a version of this where statistics along the given hyperspace are aggregated. This does not preserve last, 95 and 99 percentile values. */
    public Metric collapse(Set<String> hyperspace) {
        return new Metric(statistics.keySet().stream()
                                    .collect(toUnmodifiableMap(point -> point.keySet().stream()
                                                                             .filter(dimension -> ! hyperspace.contains(dimension))
                                                                             .collect(toUnmodifiableMap(dimension -> dimension, point::get)),
                                                               statistics::get,
                                                               Statistic::mergedWith)));
    }

    /** Returns a collapsed version of this, with all statistics aggregated. This does not preserve last, 95 and 99 percentile values. */
    public Metric collapse() {
        Map<String, ?> firstStatistic = statistics.keySet().iterator().next();
        return firstStatistic == null ? this : collapse(firstStatistic.keySet());
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
