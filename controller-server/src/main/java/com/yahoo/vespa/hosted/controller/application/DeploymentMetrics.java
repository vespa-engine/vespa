// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.time.Instant;
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

    public static final DeploymentMetrics none = new DeploymentMetrics(0, 0, 0, 0, 0, Optional.empty());

    private final double queriesPerSecond;
    private final double writesPerSecond;
    private final double documentCount;
    private final double queryLatencyMillis;
    private final double writeLatencyMills;
    private final Optional<Instant> instant;

    /* DO NOT USE. Public for serialization purposes */
    public DeploymentMetrics(double queriesPerSecond, double writesPerSecond, double documentCount,
                             double queryLatencyMillis, double writeLatencyMills, Optional<Instant> instant) {
        this.queriesPerSecond = queriesPerSecond;
        this.writesPerSecond = writesPerSecond;
        this.documentCount = documentCount;
        this.queryLatencyMillis = queryLatencyMillis;
        this.writeLatencyMills = writeLatencyMills;
        this.instant = Objects.requireNonNull(instant, "instant must be non-null");
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

    public DeploymentMetrics withQueriesPerSecond(double queriesPerSecond) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant);
    }

    public DeploymentMetrics withWritesPerSecond(double writesPerSecond) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant);
    }

    public DeploymentMetrics withDocumentCount(double documentCount) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant);
    }

    public DeploymentMetrics withQueryLatencyMillis(double queryLatencyMillis) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant);
    }

    public DeploymentMetrics withWriteLatencyMillis(double writeLatencyMills) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, instant);
    }

    public DeploymentMetrics at(Instant instant) {
        return new DeploymentMetrics(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis,
                                     writeLatencyMills, Optional.of(instant));
    }

}
