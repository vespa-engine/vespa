// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Predicate;

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

        // See warmupDuration*4 into the past to see any generation change in it.
        // If none can be detected we assume the node is new/was down.
        // If either this is the case, or there is a generation change, we ignore
        // the first warmupWindow metrics.
        var timeseries = db.getNodeTimeseries(period.plus(warmupDuration.multipliedBy(4)), clusterNodes);
        if (cluster.lastScalingEvent().isPresent()) {
            long currentGeneration = cluster.lastScalingEvent().get().generation();
            timeseries = keepGenerationAfterWarmup(timeseries, currentGeneration);
        }
        timeseries = keep(timeseries, snapshot -> snapshot.inService() && snapshot.stable());
        timeseries = keep(timeseries, snapshot -> ! snapshot.at().isBefore(db.clock().instant().minus(period)));
        this.timeseries = timeseries;
    }

    private ClusterNodesTimeseries(NodeList clusterNodes, List<NodeTimeseries> timeseries) {
        this.clusterNodes = clusterNodes;
        this.timeseries = timeseries;
    }

    public boolean isEmpty() {
        return measurementsPerNode() == 0;
    }

    /** Returns the average number of measurements per node */
    public double measurementsPerNode() {
        if (clusterNodes.size() == 0) return 0;
        return (double) totalMeasurementsIn(timeseries) / clusterNodes.size();
    }

    private int totalMeasurementsIn(List<NodeTimeseries> timeseries) {
        return timeseries.stream().mapToInt(m -> m.size()).sum();
    }

    /** Returns the number of nodes measured in this */
    public int nodesMeasured() { return timeseries.size(); }

    /**
     * Returns the "peak load" in this: Which is for each load dimension,
     * the average of the highest reading for that dimension on each node.
     */
    public Load peakLoad() {
        return new Load(peakLoad(Load.Dimension.cpu), peakLoad(Load.Dimension.memory), peakLoad(Load.Dimension.disk));
    }

    private double peakLoad(Load.Dimension dimension) {
        double total = 0;
        int count = 0;
        for (var nodeTimeseries : timeseries) {
            OptionalDouble value = nodeTimeseries.peak(dimension);
            if (value.isEmpty()) continue;
            total += value.getAsDouble();
            count++;
        }
        if (count == 0) return 0;
        return total / count;
    }

    private static List<NodeTimeseries> keep(List<NodeTimeseries> timeseries, Predicate<NodeMetricSnapshot> filter) {
        return timeseries.stream().map(nodeTimeseries -> nodeTimeseries.keep(filter)).toList();
    }

    private List<NodeTimeseries> keepGenerationAfterWarmup(List<NodeTimeseries> timeseries, long currentGeneration) {
        return timeseries.stream()
                         .map(nodeTimeseries -> nodeTimeseries.keepGenerationAfterWarmup(currentGeneration,
                                                                                         clusterNodes.node(nodeTimeseries.hostname())))
                         .toList();
    }

    public static ClusterNodesTimeseries empty() {
        return new ClusterNodesTimeseries(NodeList.of(), List.of());
    }

}
