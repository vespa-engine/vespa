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

    public ClusterMetricSnapshot(Instant at, double queryRate) {
        this.at = at;
        this.queryRate = queryRate;
    }

    public Instant at() { return at; }

    /** Queries per second */
    public double queryRate() { return queryRate; }

    public ClusterMetricSnapshot withQueryRate(double queryRate) {
        return new ClusterMetricSnapshot(at, queryRate);
    }

    @Override
    public int compareTo(ClusterMetricSnapshot other) {
        return at.compareTo(other.at);
    }

    @Override
    public String toString() { return "metrics at " + at + ":" +
                                      " queryRate: " + queryRate;
    }

}
