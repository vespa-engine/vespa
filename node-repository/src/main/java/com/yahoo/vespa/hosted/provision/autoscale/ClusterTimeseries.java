// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A series of metric snapshots for all nodes in a cluster
 *
 * @author bratseth
 */
public class ClusterTimeseries {

    private final Cluster cluster;
    private final NodeList clusterNodes;

    /** The measurements for all nodes in this snapshot */
    private final List<NodeTimeseries> timeseries;

    public ClusterTimeseries(Duration period, Cluster cluster, NodeList clusterNodes, MetricsDb db) {
        this.cluster = cluster;
        this.clusterNodes = clusterNodes;
        var timeseries = db.getNodeTimeseries(period, clusterNodes);

        if (cluster.lastScalingEvent().isPresent())
            timeseries = filter(timeseries, snapshot -> snapshot.generation() < 0 || // Content nodes do not yet send generation
                                                        snapshot.generation() >= cluster.lastScalingEvent().get().generation());
        timeseries = filter(timeseries, snapshot -> snapshot.inService() && snapshot.stable());

        this.timeseries = timeseries;
    }

    /** The cluster this is a timeseries for */
    public Cluster cluster() { return cluster; }

    /** The nodes of the cluster this is a timeseries for */
    public NodeList clusterNodes() { return clusterNodes; }

    /** Returns the average number of measurements per node */
    public int measurementsPerNode() {
        int measurementCount = timeseries.stream().mapToInt(m -> m.size()).sum();
        return measurementCount / clusterNodes.size();
    }

    /** Returns the number of nodes measured in this */
    public int nodesMeasured() {
        return timeseries.size();
    }

    /** Returns the average load of this resource in this */
    public double averageLoad(Resource resource) {
        int measurementCount = timeseries.stream().mapToInt(m -> m.size()).sum();
        if (measurementCount == 0) return 0;
        double measurementSum = timeseries.stream().flatMap(m -> m.asList().stream()).mapToDouble(m -> value(resource, m)).sum();
        return measurementSum / measurementCount;
    }

    /** The max query growth rate we can predict from this time-series as a fraction of the current traffic per minute */
    public double maxQueryGrowthRate() {
        return 0.1; // default
    }

    /** The current query rate as a fraction of the peak rate in this timeseries */
    public double currentQueryFractionOfMax() {

        return 0.5; // default
    }

    private double value(Resource resource, MetricSnapshot snapshot) {
        switch (resource) {
            case cpu: return snapshot.cpu();
            case memory: return snapshot.memory();
            case disk: return snapshot.disk();
            default: throw new IllegalArgumentException("Got an unknown resource " + resource);
        }
    }

    private List<NodeTimeseries> filter(List<NodeTimeseries> timeseries, Predicate<MetricSnapshot> filter) {
        return timeseries.stream().map(nodeTimeseries -> nodeTimeseries.filter(filter)).collect(Collectors.toList());
    }

}
