// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import java.util.Optional;

/**
 * @author olaa
 * @author ogronnesby
 */
public class DeploymentMetricsAggregator {

    private LatencyMetrics feed;
    private LatencyMetrics qr;
    private LatencyMetrics container;
    private Double documentCount;
    private Integer feedingBlocked;

    public synchronized DeploymentMetricsAggregator addFeedLatency(double sum, double count) {
        this.feed = combineLatency(this.feed, sum, count);
        return this;
    }

    public synchronized DeploymentMetricsAggregator addQrLatency(double sum, double count) {
        this.qr = combineLatency(this.qr, sum, count);
        return this;
    }

    public synchronized DeploymentMetricsAggregator addContainerLatency(double sum, double count) {
        this.container = combineLatency(this.container, sum, count);
        return this;
    }

    public synchronized DeploymentMetricsAggregator addDocumentCount(double count) {
        this.documentCount = (this.documentCount == null ? 0.0 : this.documentCount) + count;
        return this;
    }

    public synchronized DeploymentMetricsAggregator addFeedingBlocked(int feedingBlocked) {
        this.feedingBlocked = Math.max(Optional.ofNullable(this.feedingBlocked).orElse(0), feedingBlocked);
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

    public Optional<Integer> feedingBlocked() {
        return Optional.ofNullable(feedingBlocked);
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
