// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import java.util.List;

/**
 * @author olaa
 */
public class Metrics {

    private double queriesPerSecond;
    private double writesPerSecond;
    private double documentCount;
    private double queryLatencyMillis;
    private double writeLatencyMills;

    public Metrics(double queriesPerSecond, double writesPerSecond, double documentCount,
                             double queryLatencyMillis, double writeLatencyMills) {
        this.queriesPerSecond = queriesPerSecond;
        this.writesPerSecond = writesPerSecond;
        this.documentCount = documentCount;
        this.queryLatencyMillis = queryLatencyMillis;
        this.writeLatencyMills = writeLatencyMills;
    }


    public double getQueriesPerSecond() {
        return queriesPerSecond;
    }

    public double getWritesPerSecond() {
        return writesPerSecond;
    }

    public double getDocumentCount() {
        return documentCount;
    }

    public double getQueryLatencyMillis() {
        return queryLatencyMillis;
    }

    public double getWriteLatencyMills() {
        return writeLatencyMills;
    }

    public void accumulate(Metrics metrics) {
        this.queriesPerSecond += metrics.getQueriesPerSecond();
        this.writesPerSecond += metrics.getWritesPerSecond();
        this.queryLatencyMillis += metrics.getQueryLatencyMillis();
        this.writeLatencyMills += metrics.getWriteLatencyMills();
    }

    public static Metrics averagedMetrics(List<Metrics> metrics) {
        return new Metrics(
                metrics.stream().mapToDouble(Metrics::getQueriesPerSecond).sum() / metrics.size(),
                metrics.stream().mapToDouble(Metrics::getWritesPerSecond).sum() / metrics.size(),
                metrics.stream().mapToDouble(Metrics::getDocumentCount).sum() / metrics.size(),
                metrics.stream().mapToDouble(Metrics::getQueryLatencyMillis).sum() / metrics.size(),
                metrics.stream().mapToDouble(Metrics::getWriteLatencyMills).sum() / metrics.size()
        );
    }
}
