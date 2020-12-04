// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A series of metric snapshots for all nodes in a cluster
 *
 * @author bratseth
 */
public class ClusterTimeseries {

    private final NodeList clusterNodes;

    /** The measurements for all nodes in this snapshot */
    private final List<NodeTimeseries> allTimeseries;

    public ClusterTimeseries(Cluster cluster, NodeList clusterNodes, MetricsDb db, NodeRepository nodeRepository) {
        this.clusterNodes = clusterNodes;
        var timeseries = db.getNodeTimeseries(nodeRepository.clock().instant().minus(Autoscaler.scalingWindow(clusterNodes.clusterSpec(), cluster)),
                                              clusterNodes);

        if (cluster.lastScalingEvent().isPresent())
            timeseries = filter(timeseries, snapshot -> snapshot.generation() < 0 || // Content nodes do not yet send generation
                                                        snapshot.generation() >= cluster.lastScalingEvent().get().generation());
        timeseries = filter(timeseries, snapshot -> snapshot.inService() && snapshot.stable());

        this.allTimeseries = timeseries;
    }

    /** Returns the average number of measurements per node */
    public int measurementsPerNode() {
        int measurementCount = allTimeseries.stream().mapToInt(m -> m.size()).sum();
        return measurementCount / clusterNodes.size();
    }

    /** Returns the number of nodes measured in this */
    public int nodesMeasured() {
        return allTimeseries.size();
    }

    /** Returns the average load of this resource in this */
    public double averageLoad(Resource resource) {
        int measurementCount = allTimeseries.stream().mapToInt(m -> m.size()).sum();
        double measurementSum = allTimeseries.stream().flatMap(m -> m.asList().stream()).mapToDouble(m -> value(resource, m)).sum();
        return measurementSum / measurementCount;
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
