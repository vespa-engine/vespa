// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import java.util.Optional;

/**
 * @author olaa
 * @author ogronnesby
 */
public class MetricsAggregator {

    private LatencyMetrics feed;
    private LatencyMetrics qr;
    private LatencyMetrics container;
    private ProtonMetrics proton;
    private Double documentCount;

    public synchronized MetricsAggregator addFeedLatency(double sum, double count) {
        this.feed = combineLatency(this.feed, sum, count);
        return this;
    }

    public synchronized MetricsAggregator addQrLatency(double sum, double count) {
        this.qr = combineLatency(this.qr, sum, count);
        return this;
    }

    public synchronized MetricsAggregator addContainerLatency(double sum, double count) {
        this.container = combineLatency(this.container, sum, count);
        return this;
    }

    public synchronized MetricsAggregator addDocumentCount(double count) {
        this.documentCount = (this.documentCount == null ? 0.0 : this.documentCount) + count;
        return this;
    }

    public synchronized MetricsAggregator addProtonData(
            double reranked_rate,
            double allocated_bytes_last,
            double docs_matched_rate,
            double documents_active,
            double documents_ready,
            double documents_total,
            double disk_usage,
            double disk_usage_avg,
            double mem_usage_avg,
            double query_latency_avg,
            double docsum_latency_avg,
            double docsum_req_doc_rate
    ) {
        this.proton.reranked_rate += reranked_rate;
        
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

    private static class ProtonMetrics {
        double reranked_rate;
        double allocated_bytes_last;
        double docs_matched_rate;
        double documents_active;
        double documents_ready;
        double documents_total;
        double disk_usage;
        double disk_usage_avg;
        double mem_usage_avg;
        double query_latency_avg;
        double docsum_latency_avg;
        double docsum_req_doc_rate;
    }
}
