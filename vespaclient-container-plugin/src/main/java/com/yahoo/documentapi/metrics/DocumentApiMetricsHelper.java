// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.metrics;

import com.yahoo.metrics.simple.Counter;
import com.yahoo.metrics.simple.Gauge;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Point;

import java.util.HashMap;
import java.util.Map;


/**
 * @author freva
 */
public class DocumentApiMetricsHelper {
    private final Counter feeds;
    private final Gauge feedLatency;
    private final Map<DocumentOperationStatus, Map<DocumentOperationType, Point>> points = new HashMap<>();

    public DocumentApiMetricsHelper(MetricReceiver metricReceiver, String apiName) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("api", apiName);
        for (DocumentOperationStatus status : DocumentOperationStatus.values()) {
            points.put(status, new HashMap<>());
            dimensions.put("status", status.name());
            for (DocumentOperationType operation : DocumentOperationType.values()) {
                dimensions.put("operation", operation.name());
                points.get(status).put(operation, new Point(dimensions));
            }
        }

        feeds = metricReceiver.declareCounter("feed_operations");
        feedLatency = metricReceiver.declareGauge("feed_latency");
    }

    public void reportSuccessful(DocumentOperationType documentOperationType, double latency) {
        Point point = points.get(DocumentOperationStatus.OK).get(documentOperationType);

        feedLatency.sample(latency, point);
        feeds.add(point);
    }

    public void reportSuccessful(DocumentOperationType documentOperationType, long startTime) {
        final double latency = (System.currentTimeMillis() - startTime) / 1000.0d;
        reportSuccessful(documentOperationType, latency);
    }

    public void reportFailure(DocumentOperationType documentOperationType, DocumentOperationStatus documentOperationStatus) {
        Point point = points.get(documentOperationStatus).get(documentOperationType);
        feeds.add(point);
    }
}
