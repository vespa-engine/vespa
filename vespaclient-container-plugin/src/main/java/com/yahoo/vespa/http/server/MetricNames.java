// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

/**
 * Place to store the metric names so where the metrics are logged can be found
 * more easily in an IDE.
 *
 * @author steinar
 */
public final class MetricNames {

    private static final String PREFIX = "httpapi_";

    public static final String NUM_OPERATIONS = PREFIX + "num_operations";
    public static final String NUM_PUTS = PREFIX + "num_puts";
    public static final String NUM_REMOVES = PREFIX + "num_removes";
    public static final String NUM_UPDATES = PREFIX + "num_updates";
    public static final String OPERATIONS_PER_SEC = PREFIX + "ops_per_sec";
    public static final String LATENCY = PREFIX + "latency";
    public static final String FAILED = PREFIX + "failed";
    public static final String CONDITION_NOT_MET = PREFIX + "condition_not_met";
    public static final String NOT_FOUND = PREFIX + "not_found";
    public static final String PARSE_ERROR = PREFIX + "parse_error";
    public static final String SUCCEEDED = PREFIX + "succeeded";
    public static final String PENDING = PREFIX + "pending";

    private MetricNames() {
    }

}
