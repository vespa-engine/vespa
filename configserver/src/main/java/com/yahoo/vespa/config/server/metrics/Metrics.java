// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import java.time.Instant;
import java.util.List;

/**
 * @author olaa
 */
public class Metrics {

    private final double queriesPerSecond;
    private final double writesPerSecond;
    private final double documentCount;
    private final double queryLatencyMillis;
    private final double writeLatencyMills;
    private final Instant timestamp;

    public Metrics(double queriesPerSecond, double writesPerSecond, double documentCount,
                             double queryLatencyMillis, double writeLatencyMills, Instant timestamp) {
        this.queriesPerSecond = queriesPerSecond;
        this.writesPerSecond = writesPerSecond;
        this.documentCount = documentCount;
        this.queryLatencyMillis = queryLatencyMillis;
        this.writeLatencyMills = writeLatencyMills;
        this.timestamp = timestamp;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public static Metrics averagedMetrics(List<Metrics> metrics) {
        return new Metrics(
                metrics.stream().mapToDouble(Metrics::getQueriesPerSecond).sum() / metrics.size(),
                metrics.stream().mapToDouble(Metrics::getWritesPerSecond).sum() / metrics.size(),
                metrics.stream().mapToDouble(Metrics::getDocumentCount).sum() / metrics.size(),
                metrics.stream().mapToDouble(Metrics::getQueryLatencyMillis).sum() / metrics.size(),
                metrics.stream().mapToDouble(Metrics::getWriteLatencyMills).sum() / metrics.size(),
                metrics.stream().findAny().get().timestamp
        );
    }

    public static Metrics accumulatedMetrics(List<Metrics> metrics) {
        return new Metrics(
                metrics.stream().mapToDouble(Metrics::getQueriesPerSecond).sum(),
                metrics.stream().mapToDouble(Metrics::getWritesPerSecond).sum() ,
                metrics.stream().mapToDouble(Metrics::getDocumentCount).sum(),
                metrics.stream().mapToDouble(Metrics::getQueryLatencyMillis).sum(),
                metrics.stream().mapToDouble(Metrics::getWriteLatencyMills).sum(),
                metrics.stream().findAny().get().timestamp
        );
    }
}
