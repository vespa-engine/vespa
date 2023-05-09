// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import ai.vespa.metrics.ContainerMetrics;

/**
 * Place to store the metric names so where the metrics are logged can be found
 * more easily in an IDE.
 *
 * @author steinar
 */
public final class MetricNames {
    public static final String NUM_OPERATIONS = ContainerMetrics.HTTPAPI_NUM_OPERATIONS.baseName();
    public static final String NUM_PUTS = ContainerMetrics.HTTPAPI_NUM_PUTS.baseName();
    public static final String NUM_REMOVES = ContainerMetrics.HTTPAPI_NUM_REMOVES.baseName();
    public static final String NUM_UPDATES = ContainerMetrics.HTTPAPI_NUM_UPDATES.baseName();
    public static final String OPERATIONS_PER_SEC = ContainerMetrics.HTTPAPI_OPS_PER_SEC.baseName();
    public static final String LATENCY = ContainerMetrics.HTTPAPI_LATENCY.baseName();
    public static final String FAILED = ContainerMetrics.HTTPAPI_FAILED.baseName();
    public static final String CONDITION_NOT_MET = ContainerMetrics.HTTPAPI_CONDITION_NOT_MET.baseName();
    public static final String NOT_FOUND = ContainerMetrics.HTTPAPI_NOT_FOUND.baseName();
    public static final String PARSE_ERROR = ContainerMetrics.HTTPAPI_PARSE_ERROR.baseName();
    public static final String SUCCEEDED = ContainerMetrics.HTTPAPI_SUCCEEDED.baseName();
    public static final String PENDING = ContainerMetrics.HTTPAPI_PENDING.baseName();
    public static final String FAILED_UNKNOWN = ContainerMetrics.HTTPAPI_FAILED_UNKNOWN.baseName();
    public static final String FAILED_TIMEOUT = ContainerMetrics.HTTPAPI_FAILED_TIMEOUT.baseName();
    public static final String FAILED_INSUFFICIENT_STORAGE = ContainerMetrics.HTTPAPI_FAILED_INSUFFICIENT_STORAGE.baseName();

    private MetricNames() {
    }

}
