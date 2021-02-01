package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class ProtonMetrics {

    private static final Logger logger = LogManager.getLogManager().getLogger(ProtonMetrics.class.getName());

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

    public JSONObject toJson() {
        try {
            JSONObject protonMetrics = new JSONObject();
            protonMetrics.put("clusterId", clusterId);
            JSONObject jsonMetrics = new JSONObject();
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                jsonMetrics.put(entry.getKey(), entry.getValue());
            }
            protonMetrics.put("metrics", jsonMetrics);
            return protonMetrics;
        } catch (JSONException e) {
            logger.severe("Unable to convert Proton Metrics to JSON Object");
        }
        return new JSONObject();
    }
}
