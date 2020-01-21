// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import java.time.Instant;

/**
 * An in-memory time-series "database" of node metrics.
 * Thread model: One writer, many readers.
 *
 * @author bratseth
 */
public class NodeMetricsDb {

    public void update(String metric, double value, String hostname, Instant timestamp) {
        // TODO
    }

}
