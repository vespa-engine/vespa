// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.time.Instant;
import java.util.Optional;

/**
 * Metrics for a deployment of an application.
 *
 * @author smorgrav
 */
public class DeploymentMetrics {

    public static final DeploymentMetrics none = new DeploymentMetrics(0, 0, 0, 0, 0);

    private final double queriesPerSecond;
    private final double writesPerSecond;
    private final double documentCount;
    private final double queryLatencyMillis;
    private final double writeLatencyMills;
    private final Optional<Instant> instant;

    public DeploymentMetrics(double queriesPerSecond, double writesPerSecond, double documentCount,
                             double queryLatencyMillis, double writeLatencyMills) {
        this(queriesPerSecond, writesPerSecond, documentCount, queryLatencyMillis, writeLatencyMills, Optional.empty());
    }

    public DeploymentMetrics(double queriesPerSecond, double writesPerSecond, double documentCount,
                             double queryLatencyMillis, double writeLatencyMills, Optional<Instant> instant) {
        this.queriesPerSecond = queriesPerSecond;
        this.writesPerSecond = writesPerSecond;
        this.documentCount = documentCount;
        this.queryLatencyMillis = queryLatencyMillis;
        this.writeLatencyMills = writeLatencyMills;
        this.instant = instant;
    }

    public double queriesPerSecond() {
        return queriesPerSecond;
    }

    public double writesPerSecond() {
        return writesPerSecond;
    }

    public double documentCount() {
        return documentCount;
    }

    public double queryLatencyMillis() {
        return queryLatencyMillis;
    }

    public double writeLatencyMillis() {
        return writeLatencyMills;
    }

    public Optional<Instant> instant() {
        return instant;
    }

}
