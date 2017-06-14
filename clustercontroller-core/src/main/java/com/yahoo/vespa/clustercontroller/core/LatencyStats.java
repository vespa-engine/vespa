// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * LatencyStats handles adding latencies and counts.
 * @author hakonhall
 */
public class LatencyStats {

    private long latencyMsSum;
    private long count;

    public LatencyStats() { this(0, 0); }

    /**
     * @param latencyMsSum    The sum of the latencies of all RPCs (or whatever) in milliseconds.
     * @param count           The number of RPC calls (or whatever).
     */
    public LatencyStats(long latencyMsSum, long count) {
        this.latencyMsSum = latencyMsSum;
        this.count = count;
    }

    void add(LatencyStats latencyToAdd) {
        latencyMsSum += latencyToAdd.latencyMsSum;
        count += latencyToAdd.count;
    }

    public long getLatencyMsSum() { return latencyMsSum; }
    public long getCount() { return count; }
}
