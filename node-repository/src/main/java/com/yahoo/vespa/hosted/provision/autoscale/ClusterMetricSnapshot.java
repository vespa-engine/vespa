// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import java.time.Instant;

/**
 * Cluster level metrics.
 * These are aggregated at fetch time over the nodes in the cluster at that point in time.
 *
 * @author bratseth
 */
public class ClusterMetricSnapshot implements Comparable<ClusterMetricSnapshot> {

    private final Instant at;
    private final double queryRate;
    private final double writeRate;

    public ClusterMetricSnapshot(Instant at, double queryRate, double writeRate) {
        this.at = at;
        this.queryRate = queryRate;
        this.writeRate = writeRate;
    }

    public Instant at() { return at; }

    /** Queries per second */
    public double queryRate() { return queryRate; }

    /** Write operations per second */
    public double writeRate() { return writeRate; }

    public ClusterMetricSnapshot withQueryRate(double queryRate) {
        return new ClusterMetricSnapshot(at, queryRate, writeRate);
    }

    public ClusterMetricSnapshot withWriteRate(double writeRate) {
        return new ClusterMetricSnapshot(at, queryRate, writeRate);
    }

    @Override
    public int compareTo(ClusterMetricSnapshot other) {
        return at.compareTo(other.at);
    }

    @Override
    public String toString() { return "metrics at " + at + ":" +
                                      " queryRate: " + queryRate +
                                      " writeRate: " + writeRate;
    }

    public static ClusterMetricSnapshot empty(Instant instant) {
        return new ClusterMetricSnapshot(instant, 0.0, 0.0);
    }

}
