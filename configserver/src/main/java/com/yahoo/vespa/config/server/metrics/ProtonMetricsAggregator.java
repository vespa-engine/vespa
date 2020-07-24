package com.yahoo.vespa.config.server.metrics;

import com.yahoo.slime.Inspector;

public class ProtonMetricsAggregator {

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

    private Double resourceDiskUsageAverage = 0.0;
    private Double resourceMemoryUsageAverage = 0.0;

    public synchronized ProtonMetricsAggregator addAll(Inspector metric) {
        addDocumentActiveCount(metric.field(DOCUMENT_ACTIVE).asDouble());
        addDocumentReadyCount(metric.field(DOCUMENT_READY).asDouble());
        addDocumentTotalCount(metric.field(DOCUMENT_TOTAL).asDouble());
        addDocumentDiskUsage(metric.field(DOCUMENT_DISK_USAGE).asDouble());
        addResourceDiskUsageAverage(metric.field(RESOURCE_DISK_AVERAGE).asDouble());
        addResourceMemoryUsageAverage(metric.field(RESOURCE_MEMORY_AVERAGE).asDouble());
        return this;
    }

    public ProtonMetricsAggregator addAll(ProtonMetricsAggregator aggregator) {
        this.documentActiveCount += aggregator.aggregateDocumentActiveCount();
        this.documentReadyCount += aggregator.aggregateDocumentReadyCount();
        this.documentTotalCount += aggregator.aggregateDocumentTotalCount();
        this.documentDiskUsage += aggregator.aggregateDocumentDiskUsage();
        this.resourceDiskUsageAverage += aggregator.aggregateResourceDiskUsageAverage();
        this.resourceMemoryUsageAverage += aggregator.aggregateResourceMemoryUsageAverage();
        return this;
    }

    public synchronized ProtonMetricsAggregator addDocumentActiveCount(double documentActiveCount) {
        this.documentActiveCount += documentActiveCount;
        return this;
    }

    public synchronized ProtonMetricsAggregator addDocumentReadyCount(double documentReadyCount) {
        this.documentReadyCount += documentReadyCount;
        return this;
    }

    public synchronized ProtonMetricsAggregator addDocumentTotalCount(double documentTotalCount) {
        this.documentTotalCount += documentTotalCount;
        return this;
    }

    public synchronized ProtonMetricsAggregator addDocumentDiskUsage(double documentDiskUsage) {
        this.documentDiskUsage += documentDiskUsage;
        return this;
    }

    public synchronized ProtonMetricsAggregator addResourceDiskUsageAverage(double resourceDiskUsageAverage) {
        this.resourceDiskUsageAverage += resourceDiskUsageAverage;
        return this;
    }

    public synchronized ProtonMetricsAggregator addResourceMemoryUsageAverage(double resourceMemoryUsageAverage) {
        this.resourceMemoryUsageAverage += resourceMemoryUsageAverage;
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

    public Double aggregateDocumentDiskUsage() {
        return this.documentDiskUsage;
    }

    public Double aggregateResourceDiskUsageAverage() {
        return this.resourceDiskUsageAverage;
    }

    public Double aggregateResourceMemoryUsageAverage() {
        return this.resourceMemoryUsageAverage;
    }

}
