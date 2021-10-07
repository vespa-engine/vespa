// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Metrics for a deployment of an application. This contains a snapshot of metrics gathered at a point in time, it does
 * not contain any historical data.
 *
 * @author smorgrav
 * @author mpolden
 */
public class DeploymentMetrics {

    public static final DeploymentMetrics none = new DeploymentMetrics(0, 0, 0, 0, 0, Optional.empty(), Map.of());

    private final double queriesPerSecond;
    private final double writesPerSecond;
    private final double documentCount;
    private final double queryLatencyMillis;
    private final double writeLatencyMills;
    private final Optional<Instant> instant;
    private final Map<Warning, Integer> warnings;

    /* DO NOT USE. Public for serialization purposes */
    public DeploymentMetrics(double queriesPerSecond, double writesPerSecond, double documentCount,
                             double queryLatencyMillis, double writeLatencyMills, Optional<Instant> instant,
                             Map<Warning, Integer> warnings) {
        this.queriesPerSecond = queriesPerSecond;
        this.writesPerSecond = writesPerSecond;
        this.documentCount = documentCount;
        this.queryLatencyMillis = queryLatencyMillis;
        this.writeLatencyMills = writeLatencyMills;
        this.instant = Objects.requireNonNull(instant, "instant must be non-null");
        this.warnings = Map.copyOf(Objects.requireNonNull(warnings, "warnings must be non-null"));
        if (warnings.entrySet().stream().anyMatch(kv -> kv.getValue() < 0)) {
            throw new IllegalArgumentException("Warning count must be non-negative. Got " + warnings);
        }
    }

    /** Returns the number of queries per second */
    public double queriesPerSecond() {
        return queriesPerSecond;
    }

    /** Returns the number of writes per second */
    public double writesPerSecond() {
        return writesPerSecond;
    }

    /** Returns the number of documents */
    public double documentCount() {
        return documentCount;
    }

    /** Returns the average query latency in milliseconds */
    public double queryLatencyMillis() {
        return queryLatencyMillis;
    }

    /** Returns the average write latency in milliseconds */
    public double writeLatencyMillis() {
        return writeLatencyMills;
    }

    /** Returns the approximate time this was measured */
    public Optional<Instant> instant() {
        return instant;
    }

    /** Returns the number of warnings of the most recent deployment */
    public Map<Warning, Integer> warnings() {
        return warnings;
    }

    public DeploymentMetrics withQueriesPerSecond(double queriesPerSecond) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant, warnings);
    }

    public DeploymentMetrics withWritesPerSecond(double writesPerSecond) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant, warnings);
    }

    public DeploymentMetrics withDocumentCount(double documentCount) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant, warnings);
    }

    public DeploymentMetrics withQueryLatencyMillis(double queryLatencyMillis) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant, warnings);
    }

    public DeploymentMetrics withWriteLatencyMillis(double writeLatencyMills) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant, warnings);
    }

    public DeploymentMetrics at(Instant instant) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, Optional.of(instant), warnings);
    }

    public DeploymentMetrics with(Map<Warning, Integer> warnings) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant, warnings);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeploymentMetrics that = (DeploymentMetrics) o;
        return Double.compare(that.queriesPerSecond, queriesPerSecond) == 0 &&
               Double.compare(that.writesPerSecond, writesPerSecond) == 0 &&
               Double.compare(that.documentCount, documentCount) == 0 &&
               Double.compare(that.queryLatencyMillis, queryLatencyMillis) == 0 &&
               Double.compare(that.writeLatencyMills, writeLatencyMills) == 0 &&
               instant.equals(that.instant) &&
               warnings.equals(that.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis, writeLatencyMills, instant, warnings);
    }

    /** Types of deployment warnings. We currently have only one */
    public enum Warning {
        all
    }

}
