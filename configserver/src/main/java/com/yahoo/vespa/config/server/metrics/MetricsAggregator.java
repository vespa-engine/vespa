// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import java.time.Instant;
import java.util.Optional;

/**
 * @author olaa
 * @author ogronnesby
 */
public class MetricsAggregator {

    private LatencyMetrics feed;
    private LatencyMetrics qr;
    private LatencyMetrics container;
    private Double documentCount;
    private Instant timestamp;

    public MetricsAggregator addFeedLatency(double sum, double count) {
        this.feed = combineLatency(this.feed, sum, count);
        return this;
    }

    public MetricsAggregator addQrLatency(double sum, double count) {
        this.qr = combineLatency(this.qr, sum, count);
        return this;
    }

    public MetricsAggregator addContainerLatency(double sum, double count) {
        this.container = combineLatency(this.container, sum, count);
        return this;
    }

    public MetricsAggregator addDocumentCount(double count) {
        this.documentCount = (this.documentCount == null ? 0.0 : this.documentCount) + count;
        return this;
    }

    public MetricsAggregator setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public Optional<Double> aggregateFeedLatency() {
        return Optional.ofNullable(feed).map(m -> m.latencySum / m.latencyCount).filter(num -> !num.isNaN());

    }

    public Optional<Double> aggregateFeedRate() {
        return Optional.ofNullable(feed).map(m -> m.latencyCount / 60);
    }

    public Optional<Double> aggregateQueryLatency() {
        if (container == null && qr == null) return Optional.empty();
        var c = Optional.ofNullable(container).orElseGet(LatencyMetrics::new);
        var q = Optional.ofNullable(qr).orElseGet(LatencyMetrics::new);
        return Optional.of((c.latencySum + q.latencySum) / (c.latencyCount + q.latencyCount)).filter(num -> !num.isNaN());
    }

    public Optional<Double> aggregateQueryRate() {
        if (container == null && qr == null) return Optional.empty();
        var c = Optional.ofNullable(container).orElseGet(LatencyMetrics::new);
        var q = Optional.ofNullable(qr).orElseGet(LatencyMetrics::new);
        return Optional.of((c.latencyCount + q.latencyCount) / 60);
    }

    public Optional<Double> aggregateDocumentCount() {
        return Optional.ofNullable(documentCount);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    private LatencyMetrics combineLatency(LatencyMetrics metricsOrNull, double sum, double count) {
        var metrics = Optional.ofNullable(metricsOrNull).orElseGet(LatencyMetrics::new);
        metrics.latencyCount += count;
        metrics.latencySum += sum;
        return metrics;
    }

    private static class LatencyMetrics {
        double latencySum;
        double latencyCount;
    }
}
