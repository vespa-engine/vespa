// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.metric;

import ai.vespa.hosted.cd.Endpoint;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringJoiner;

import static java.util.Map.copyOf;

/**
 * Metrics from a Vespa application {@link Endpoint}, indexed by their names, and optionally by a set of custom dimensions.
 *
 * Metrics are collected from the <a href="https://docs.vespa.ai/documentation/reference/metrics-health-format.html">metrics</a>
 * API of a Vespa endpoint, and contain the current health status of the endpoint, values for all configured metrics in
 * that endpoint, and the time interval from which these metrics were sampled.
 *
 * Each metric is indexed by a name, and, optionally, along a custom set of dimensions, given by a {@code Map<String, String>}.
 *
 * @author jonmv
 */
public class Metrics {

    private final Instant start, end;
    private final Map<String, Metric> metrics;

    private Metrics(Instant start, Instant end, Map<String, Metric> metrics) {
        this.start = start;
        this.end = end;
        this.metrics = metrics;
    }

    public static Metrics of(Instant start, Instant end, Map<String, Metric> metrics) {
        if ( ! start.isBefore(end))
            throw new IllegalArgumentException("Given time interval must be positive: '" + start + "' to '" + end + "'.");

        return new Metrics(start, end, copyOf(metrics));
    }

    /** Returns the start of the time window from which these metrics were sampled, or throws if the status is {@code Status.down}. */
    public Instant start() {
        return start;
    }

    /** Returns the end of the time window from which these metrics were sampled, or throws if the status is {@code Status.down}. */
    public Instant end() {
        return end;
    }

    /** Returns the metric with the given name, or throws a NoSuchElementException if no such Metric is known. */
    public Metric get(String name) {
        if ( ! metrics.containsKey(name))
            throw new NoSuchElementException("No metric with name '" + name + "'.");

        return metrics.get(name);
    }

    /** Returns the underlying, unmodifiable Map. */
    public Map<String, Metric> asMap() {
        return metrics;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Metrics.class.getSimpleName() + "[", "]")
                .add("start=" + start)
                .add("end=" + end)
                .add("metrics=" + metrics)
                .toString();
    }

}
