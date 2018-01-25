// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

/**
 * How many nodes can suspend concurrently, at most.
 */
public enum ConcurrentSuspensionLimitForCluster {
    ONE_NODE(0),
    TEN_PERCENT(10),
    TWENTY_PERCENT(20),
    ALL_NODES(100);

    int percentage;

    ConcurrentSuspensionLimitForCluster(int percentage) {
        this.percentage = percentage;
    }

    public int asPercentage() {
        return percentage;
    }
}
