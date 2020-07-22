package com.yahoo.vespa.config.server.metrics;

import com.yahoo.slime.Inspector;
import java.util.List;

public class ProtonMetricsAggregator {

    private static final List<String> DESIRED_METRICS = List.of(
            "content.proton.documentdb.documents.active.last",
            "content.proton.documentdb.documents.ready.last",
            "content.proton.documentdb.documents.total.last",
            "content.proton.documentdb.disk_usage.last",
            "content.proton.resource_usage.disk.average",
            "content.proton.resource_usage.memory.average"
    );

    private Double documentActiveCount = 0.0;
    private Double documentReadyCount = 0.0;
    private Double documentTotalCount = 0.0;
    private Double documentDiskUsage = 0.0;

    private Double resourceDiskUsageAverage = 0.0;
    private Double resourceMemoryUsageAverage = 0.0;

    public synchronized ProtonMetricsAggregator addAll(Inspector metric) {
        addDocumentActiveCount(metric.field(DESIRED_METRICS.get(0)).asDouble());
        addDocumentReadyCount(metric.field(DESIRED_METRICS.get(1)).asDouble());
        addDocumentTotalCount(metric.field(DESIRED_METRICS.get(2)).asDouble());
        addDocumentDiskUsage(metric.field(DESIRED_METRICS.get(3)).asDouble());
        addResourceDiskUsageAverage(metric.field(DESIRED_METRICS.get(4)).asDouble());
        addResourceMemoryUsageAverage(metric.field(DESIRED_METRICS.get(5)).asDouble());
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
