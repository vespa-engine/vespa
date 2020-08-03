package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.util.HashMap;
import java.util.Map;

public class ProtonMetrics {

    public static final String DOCUMENTS_ACTIVE_COUNT = "documentsActiveCount";
    public static final String DOCUMENTS_READY_COUNT = "documentsReadyCount";
    public static final String DOCUMENTS_TOTAL_COUNT = "documentsTotalCount";
    public static final String DOCUMENT_DISK_USAGE = "documentDiskUsage";
    public static final String RESOURCE_DISK_USAGE_AVERAGE = "resourceDiskUsageAverage";
    public static final String RESOURCE_MEMORY_USAGE_AVERAGE = "resourceMemoryUsageAverage";

    private final String clusterId;
    private final Map<String, Double> metrics;

    public ProtonMetrics(String clusterId) {
        this.clusterId = clusterId;
        metrics = new HashMap<>();
    }

    public String getClusterId() { return clusterId; }

    public double documentsActiveCount() { return metrics.get(DOCUMENTS_ACTIVE_COUNT); }

    public double documentsReadyCount() { return metrics.get(DOCUMENTS_READY_COUNT); }

    public double documentsTotalCount() { return metrics.get(DOCUMENTS_TOTAL_COUNT); }

    public double documentDiskUsage() { return metrics.get(DOCUMENT_DISK_USAGE); }

    public double resourceDiskUsageAverage() { return metrics.get(RESOURCE_DISK_USAGE_AVERAGE); }

    public double resourceMemoryUsageAverage() { return metrics.get(RESOURCE_MEMORY_USAGE_AVERAGE); }

    public ProtonMetrics addMetric(String name, double value) {
        metrics.put(name, value);
        return this;
    }

}
