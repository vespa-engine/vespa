// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.slime.Inspector;

public class SearchNodeMetricsAggregator {

    private static final String DOCUMENT_ACTIVE = "content.proton.documentdb.documents.active.last";
    private static final String DOCUMENT_READY = "content.proton.documentdb.documents.ready.last";
    private static final String DOCUMENT_TOTAL = "content.proton.documentdb.documents.total.last";
    private static final String DOCUMENT_DISK_USAGE = "content.proton.documentdb.disk_usage.last";
    private static final String RESOURCE_DISK_AVERAGE = "content.proton.resource_usage.disk.average";
    private static final String RESOURCE_MEMORY_AVERAGE = "content.proton.resource_usage.memory.average";

    private Double documentActiveCount = 0.0;
    private Double documentReadyCount = 0.0;
    private Double documentTotalCount = 0.0;
    private Double documentDiskUsage = 0.0;

    private final AverageMetric resourceDiskUsageAverage = new AverageMetric();
    private final AverageMetric resourceMemoryUsageAverage = new AverageMetric();

    public synchronized SearchNodeMetricsAggregator addAll(Inspector metric) {
        if (metric.field(DOCUMENT_ACTIVE).valid()) addDocumentActiveCount(metric.field(DOCUMENT_ACTIVE).asDouble());
        if (metric.field(DOCUMENT_READY).valid()) addDocumentReadyCount(metric.field(DOCUMENT_READY).asDouble());
        if (metric.field(DOCUMENT_TOTAL).valid()) addDocumentTotalCount(metric.field(DOCUMENT_TOTAL).asDouble());
        if (metric.field(DOCUMENT_DISK_USAGE).valid()) addDocumentDiskUsage(metric.field(DOCUMENT_DISK_USAGE).asDouble());
        if (metric.field(RESOURCE_DISK_AVERAGE).valid()) addResourceDiskUsageAverage(metric.field(RESOURCE_DISK_AVERAGE).asDouble());
        if (metric.field(RESOURCE_MEMORY_AVERAGE).valid()) addResourceMemoryUsageAverage(metric.field(RESOURCE_MEMORY_AVERAGE).asDouble());
        return this;
    }

    public SearchNodeMetricsAggregator addAll(SearchNodeMetricsAggregator aggregator) {
        this.documentActiveCount += aggregator.aggregateDocumentActiveCount();
        this.documentReadyCount += aggregator.aggregateDocumentReadyCount();
        this.documentTotalCount += aggregator.aggregateDocumentTotalCount();
        this.documentDiskUsage += aggregator.aggregateDocumentDiskUsage();
        addResourceDiskUsageAverage(aggregator);
        addResourceMemoryUsageAverage(aggregator);
        return this;
    }

    public SearchNodeMetricsAggregator addResourceDiskUsageAverage(SearchNodeMetricsAggregator aggregator) {
        this.resourceDiskUsageAverage.averageCount += aggregator.resourceDiskUsageAverage.averageCount;
        this.resourceDiskUsageAverage.averageSum += aggregator.resourceDiskUsageAverage.averageSum;
        return this;
    }

    public SearchNodeMetricsAggregator addResourceMemoryUsageAverage(SearchNodeMetricsAggregator aggregator) {
        this.resourceMemoryUsageAverage.averageCount += aggregator.resourceMemoryUsageAverage.averageCount;
        this.resourceMemoryUsageAverage.averageSum += aggregator.resourceMemoryUsageAverage.averageSum;
        return this;
    }

    public synchronized SearchNodeMetricsAggregator addDocumentActiveCount(double documentActiveCount) {
        this.documentActiveCount += documentActiveCount;
        return this;
    }

    public synchronized SearchNodeMetricsAggregator addDocumentReadyCount(double documentReadyCount) {
        this.documentReadyCount += documentReadyCount;
        return this;
    }

    public synchronized SearchNodeMetricsAggregator addDocumentTotalCount(double documentTotalCount) {
        this.documentTotalCount += documentTotalCount;
        return this;
    }

    public synchronized SearchNodeMetricsAggregator addDocumentDiskUsage(double documentDiskUsage) {
        this.documentDiskUsage += documentDiskUsage;
        return this;
    }

    public synchronized SearchNodeMetricsAggregator addResourceDiskUsageAverage(double resourceDiskUsageAverage) {
        this.resourceDiskUsageAverage.averageCount++;
        this.resourceDiskUsageAverage.averageSum += resourceDiskUsageAverage;
        return this;
    }

    public synchronized SearchNodeMetricsAggregator addResourceMemoryUsageAverage(double resourceMemoryUsageAverage) {
        this.resourceMemoryUsageAverage.averageCount++;
        this.resourceMemoryUsageAverage.averageSum += resourceMemoryUsageAverage;
        return this;
    }

    public Double aggregateDocumentActiveCount() {
        return this.documentActiveCount;
    }

    public Double aggregateDocumentReadyCount() {
        return this.documentReadyCount;
    }

    public Double aggregateDocumentTotalCount() {
        return this.documentTotalCount;
    }

    public Double aggregateDocumentDiskUsage() {return this.documentDiskUsage;}

    public Double aggregateResourceDiskUsageAverage() {
        return this.resourceDiskUsageAverage.averageSum / this.resourceDiskUsageAverage.averageCount;
    }

    public Double aggregateResourceMemoryUsageAverage() {
        return this.resourceMemoryUsageAverage.averageSum / this.resourceMemoryUsageAverage.averageCount;
    }

    private static class AverageMetric {
        double averageSum = 0.0;
        double averageCount = 0.0;
    }

}
