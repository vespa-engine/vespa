// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A list of metric snapshots from a cluster, sorted by increasing time (newest last).
 *
 * @author bratseth
 */
public class ClusterTimeseries {

    private final ClusterSpec.Id cluster;
    private final List<ClusterMetricSnapshot> snapshots;

    ClusterTimeseries(ClusterSpec.Id cluster, List<ClusterMetricSnapshot> snapshots) {
        this.cluster = cluster;
        List<ClusterMetricSnapshot> sortedSnapshots = new ArrayList<>(snapshots);
        Collections.sort(sortedSnapshots);
        this.snapshots = Collections.unmodifiableList(sortedSnapshots);
    }

    public boolean isEmpty() { return snapshots.isEmpty(); }

    public int size() { return snapshots.size(); }

    public ClusterMetricSnapshot get(int index) { return snapshots.get(index); }

    public List<ClusterMetricSnapshot> asList() { return snapshots; }

    public ClusterSpec.Id cluster() { return cluster; }

    public ClusterTimeseries add(ClusterMetricSnapshot snapshot) {
        List<ClusterMetricSnapshot> list = new ArrayList<>(snapshots);
        list.add(snapshot);
        return new ClusterTimeseries(cluster, list);
    }

    /** The max query growth rate we can predict from this time-series as a fraction of the current traffic per minute */
    public double maxQueryGrowthRate() {
        return 0.1; // default
    }

    /** The current query rate as a fraction of the peak rate in this timeseries */
    public double currentQueryFractionOfMax() {
        return 0.5; // default
    }

}
