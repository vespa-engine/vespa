// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import java.util.HashMap;
import java.util.Map;
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
    private ResourceUsage memoryUsage;
    private ResourceUsage diskUsage;
    private Map<String, Double> reindexingProgress;

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

    public synchronized DeploymentMetricsAggregator addDiskUsage(double feedBlockUtil, double feedBlockLimit) {
        this.diskUsage = combineResourceUtil(this.diskUsage, feedBlockUtil, feedBlockLimit);
        return this;
    }

    public synchronized DeploymentMetricsAggregator addMemoryUsage(double feedBlockUtil, double feedBlockLimit) {
        this.memoryUsage = combineResourceUtil(this.memoryUsage, feedBlockUtil, feedBlockLimit);
        return this;
    }

    public synchronized DeploymentMetricsAggregator addReindexingProgress(String documentType, double progress) {
        if (reindexingProgress == null) this.reindexingProgress = new HashMap<>();
        this.reindexingProgress.put(documentType, progress);
        return this;
    }

    public Optional<Double> aggregateFeedLatency() {
        return Optional.ofNullable(feed).map(m -> m.sum / m.count).filter(num -> !num.isNaN());
    }

    public Optional<Double> aggregateFeedRate() {
        return Optional.ofNullable(feed).map(m -> m.count / 60);
    }

    public Optional<Double> aggregateQueryLatency() {
        if (container == null && qr == null) return Optional.empty();
        var c = Optional.ofNullable(container).orElseGet(LatencyMetrics::new);
        var q = Optional.ofNullable(qr).orElseGet(LatencyMetrics::new);
        return Optional.of((c.sum + q.sum) / (c.count + q.count)).filter(num -> !num.isNaN());
    }

    public Optional<Double> aggregateQueryRate() {
        if (container == null && qr == null) return Optional.empty();
        var c = Optional.ofNullable(container).orElseGet(LatencyMetrics::new);
        var q = Optional.ofNullable(qr).orElseGet(LatencyMetrics::new);
        return Optional.of((c.count + q.count) / 60);
    }

    public Optional<Double> aggregateDocumentCount() {
        return Optional.ofNullable(documentCount);
    }

    public Optional<ResourceUsage> memoryUsage() {
        return Optional.ofNullable(memoryUsage);
    }

    public Optional<ResourceUsage> diskUsage() {
        return Optional.ofNullable(diskUsage);
    }

    public Optional<Map<String, Double>> reindexingProgress() {
        return Optional.ofNullable(reindexingProgress);
    }


    private static LatencyMetrics combineLatency(LatencyMetrics metricsOrNull, double sum, double count) {
        return Optional.ofNullable(metricsOrNull).orElseGet(LatencyMetrics::new).combine(sum, count);
    }

    private static ResourceUsage combineResourceUtil(ResourceUsage resourceUsageOrNull, double util, double limit) {
        return Optional.ofNullable(resourceUsageOrNull).orElseGet(ResourceUsage::new).combine(util, limit);
    }

    private static class LatencyMetrics {
        private double sum;
        private double count;

        private LatencyMetrics combine(double sum, double count) {
            this.sum += sum;
            this.count += count;
            return this;
        }
    }

    public static class ResourceUsage {
        /**
         * Current resource utilization relative to feed block limit, i.e. value of >= 1 means utilization at or above
         * feed block limit.
         */
        private double feedBlockUtil;

        /** Resource utilization limit at which further external feed is blocked */
        private double feedBlockLimit;

        private ResourceUsage combine(double feedBlockUtil, double feedBlockLimit) {
            if (feedBlockUtil > this.feedBlockUtil) this.feedBlockUtil = feedBlockUtil;
            if (feedBlockLimit > this.feedBlockLimit) this.feedBlockLimit = feedBlockLimit;
            return this;
        }

        public double util() { return feedBlockUtil * feedBlockLimit; }
        public double feedBlockLimit() { return feedBlockLimit; }
    }
}
