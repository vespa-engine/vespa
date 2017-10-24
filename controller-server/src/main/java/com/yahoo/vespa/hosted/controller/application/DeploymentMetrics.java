package com.yahoo.vespa.hosted.controller.application;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @author smorgrav
 */
public class DeploymentMetrics {

    private final double queriesPerSecond;
    private final double writesPerSecond;
    private final double documentCount;
    private final double queryLatencyMillis;
    private final double writeLatencyMills;

    DeploymentMetrics() {
        this.queriesPerSecond = 0;
        this.writesPerSecond = 0;
        this.documentCount = 0;
        this.queryLatencyMillis = 0;
        this.writeLatencyMills = 0;
    }

    public DeploymentMetrics(double queriesPerSecond, double writesPerSecond, double documentCount,
                             double queryLatencyMillis, double writeLatencyMills) {
        this.queriesPerSecond = queriesPerSecond;
        this.writesPerSecond = writesPerSecond;
        this.documentCount = documentCount;
        this.queryLatencyMillis = queryLatencyMillis;
        this.writeLatencyMills = writeLatencyMills;
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

}
