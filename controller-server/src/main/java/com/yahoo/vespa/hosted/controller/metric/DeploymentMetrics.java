// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.metric;

/**
 * Metrics for a single deployment of an application.
 *
 * @author bratseth
 */
public class DeploymentMetrics {

    private final double queriesPerSecond;
    private final double writesPerSecond;
    private final long documentCount;
    private final double queryLatencyMillis;
    private final double writeLatencyMillis;

    public DeploymentMetrics(double queriesPerSecond, double writesPerSecond,
                             long documentCount,
                             double queryLatencyMillis, double writeLatencyMillis) {
        this.queriesPerSecond = queriesPerSecond;
        this.writesPerSecond = writesPerSecond;
        this.documentCount = documentCount;
        this.queryLatencyMillis = queryLatencyMillis;
        this.writeLatencyMillis = writeLatencyMillis;
    }

    public double queriesPerSecond() {
        return queriesPerSecond;
    }

    public double writesPerSecond() {
        return writesPerSecond;
    }

    public long documentCount() {
        return documentCount;
    }

    public double queryLatencyMillis() {
        return queryLatencyMillis;
    }

    public double writeLatencyMillis() {
        return writeLatencyMillis;
    }

}
