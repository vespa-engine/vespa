// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A series of metric snapshots for the nodes of a cluster used to compute load
 *
 * @author bratseth
 */
public class ClusterNodesTimeseries {

    private final NodeList clusterNodes;

    /** The measurements for all nodes in this snapshot */
    private final List<NodeTimeseries> timeseries;

    public ClusterNodesTimeseries(Duration period, Cluster cluster, NodeList clusterNodes, MetricsDb db) {
        this.clusterNodes = clusterNodes;
        var timeseries = db.getNodeTimeseries(period, clusterNodes);

        if (cluster.lastScalingEvent().isPresent())
            timeseries = filter(timeseries, snapshot -> snapshot.generation() < 0 || // Content nodes do not yet send generation
                                                        snapshot.generation() >= cluster.lastScalingEvent().get().generation());
        timeseries = filter(timeseries, snapshot -> snapshot.inService() && snapshot.stable());

        this.timeseries = timeseries;
    }

    private ClusterNodesTimeseries(NodeList clusterNodes, List<NodeTimeseries> timeseries) {
        this.clusterNodes = clusterNodes;
        this.timeseries = timeseries;
    }

    /** Returns the average number of measurements per node */
    public int measurementsPerNode() {
        int measurementCount = timeseries.stream().mapToInt(m -> m.size()).sum();
        return measurementCount / clusterNodes.size();
    }

    /** Returns the number of nodes measured in this */
    public int nodesMeasured() { return timeseries.size(); }

    /** Returns the average load after the given instant */
    public Load averageLoad(Instant start) {
        Load total = Load.zero();
        int count = 0;
        for (var nodeTimeseries : timeseries) {
            for (var snapshot : nodeTimeseries.asList()) {
                if (snapshot.at().isBefore(start)) continue;
                total = total.add(snapshot.load());
                count++;
            }
        }
        return total.divide(count);
    }

    private List<NodeTimeseries> filter(List<NodeTimeseries> timeseries, Predicate<NodeMetricSnapshot> filter) {
        return timeseries.stream().map(nodeTimeseries -> nodeTimeseries.filter(filter)).collect(Collectors.toList());
    }

    public static ClusterNodesTimeseries empty() {
        return new ClusterNodesTimeseries(NodeList.of(), List.of());
    }

}
