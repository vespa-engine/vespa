// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.provision.autoscale.ClusterModel.warmupDuration;

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

        // See warmupSeconds*4 into the past to see any generation change in it
        // If none can be detected we assume the node is new/was down.
        // If either this is the case, or there is a generation change, we ignore
        // the first warmupWindow metrics
        var timeseries = db.getNodeTimeseries(period.plus(warmupDuration.multipliedBy(8)), clusterNodes);
        if (cluster.lastScalingEvent().isPresent()) {
            long currentGeneration = cluster.lastScalingEvent().get().generation();
            timeseries = keepCurrentGenerationAfterWarmup(timeseries, currentGeneration);
        }
        timeseries = keep(timeseries, snapshot -> snapshot.inService() && snapshot.stable());
        timeseries = keep(timeseries, snapshot -> ! snapshot.at().isBefore(db.clock().instant().minus(period)));
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

    private static List<NodeTimeseries> keep(List<NodeTimeseries> timeseries, Predicate<NodeMetricSnapshot> filter) {
        return timeseries.stream().map(nodeTimeseries -> nodeTimeseries.keep(filter)).collect(Collectors.toList());
    }

    private static List<NodeTimeseries> keepCurrentGenerationAfterWarmup(List<NodeTimeseries> timeseries,
                                                                         long currentGeneration) {
        return timeseries.stream()
                         .map(nodeTimeseries -> nodeTimeseries.keepCurrentGenerationAfterWarmup(currentGeneration))
                         .collect(Collectors.toList());
    }

    public static ClusterNodesTimeseries empty() {
        return new ClusterNodesTimeseries(NodeList.of(), List.of());
    }

}
