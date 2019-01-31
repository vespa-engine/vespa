// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.metrics;

import com.yahoo.metrics.simple.Counter;
import com.yahoo.metrics.simple.Gauge;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;


/**
 * This class reports metrics for feed operations by APIs that use documentapi.
 *
 * @author freva
 */
public class DocumentApiMetrics {

    private final Counter feeds;
    private final Gauge feedLatency;
    private final Counter feedRequests;
    private final Map<DocumentOperationStatus, Map<DocumentOperationType, Point>> points = new HashMap<>();
    private final Map<String, Point> versionPointCache = new HashMap<>();

    public DocumentApiMetrics(MetricReceiver metricReceiver, String apiName) {
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

        feeds = metricReceiver.declareCounter("feed.operations");
        feedLatency = metricReceiver.declareGauge("feed.latency");
        feedRequests = metricReceiver.declareCounter("feed.http-requests");
    }

    public void reportSuccessful(DocumentOperationType documentOperationType, double latencyInSeconds) {
        Point point = points.get(DocumentOperationStatus.OK).get(documentOperationType);

        feedLatency.sample(latencyInSeconds, point);
        feeds.add(point);
    }

    public void reportSuccessful(DocumentOperationType documentOperationType, Instant startTime) {
        double latency = Duration.between(startTime, Instant.now()).toMillis() / 1000.0d;
        reportSuccessful(documentOperationType, latency);
    }

    public void reportFailure(DocumentOperationType documentOperationType, DocumentOperationStatus documentOperationStatus) {
        Point point = points.get(documentOperationStatus).get(documentOperationType);
        feeds.add(point);
    }

    public void reportHttpRequest(String clientVersion) {
        if (clientVersion != null) {
            feedRequests.add(versionPointCache.computeIfAbsent(clientVersion, v -> new Point(Map.of("client-version", v))));
        } else {
            feedRequests.add();
        }
    }

}
