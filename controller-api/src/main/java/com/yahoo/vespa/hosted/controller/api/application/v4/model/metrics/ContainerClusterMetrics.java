// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model.metrics;

/**
 * @author olaa
 */
public class ContainerClusterMetrics {

    private final double queriesPerSecond;
    private final double writesPerSecond;
    private final double queryLatencyMillis;
    private final double writeLatencyMills;

    public ContainerClusterMetrics(String clusterId, double queriesPerSecond, double writesPerSecond, double queryLatencyMillis, double writeLatencyMills) {
        this.queriesPerSecond = queriesPerSecond;
        this.writesPerSecond = writesPerSecond;
        this.queryLatencyMillis = queryLatencyMillis;
        this.writeLatencyMills = writeLatencyMills;
    }


}
