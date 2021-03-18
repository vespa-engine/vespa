// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;

import java.time.Duration;

/**
 * A cluster with its associated metrics which allows prediction about its future behavior.
 * For single-threaded, short-term usage.
 *
 * @author bratseth
 */
public class ClusterModel {

    private final Application application;
    private final Cluster cluster;
    private final NodeList nodes;
    private final MetricsDb metricsDb;
    private final NodeRepository nodeRepository;

    // Lazily initialized members
    private ClusterNodesTimeseries nodeTimeseries = null;
    private ClusterTimeseries clusterTimeseries = null;

    public ClusterModel(Application application,
                        Cluster cluster,
                        NodeList clusterNodes,
                        MetricsDb metricsDb,
                        NodeRepository nodeRepository) {
        this.application = application;
        this.cluster = cluster;
        this.nodes = clusterNodes;
        this.metricsDb = metricsDb;
        this.nodeRepository = nodeRepository;
    }

    public ClusterNodesTimeseries nodeTimeseries() {
        if (nodeTimeseries != null) return nodeTimeseries;
        return nodeTimeseries = new ClusterNodesTimeseries(scalingDuration(), cluster, nodes, metricsDb);
    }

    public ClusterTimeseries clusterTimeseries() {
        if (clusterTimeseries != null) return clusterTimeseries;
        return clusterTimeseries = metricsDb.getClusterTimeseries(application.id(), cluster.id());
    }

    public boolean isStable() {
        return isStable(nodes, nodeRepository);
    }

    public static boolean isStable(NodeList clusterNodes, NodeRepository nodeRepository) {
        // The cluster is processing recent changes
        if (clusterNodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                            node.allocation().get().membership().retired() ||
                                            node.allocation().get().isRemovable()))
            return false;

        // A deployment is ongoing
        if (nodeRepository.nodes().list(Node.State.reserved).owner(clusterNodes.first().get().allocation().get().owner()).size() > 0)
            return false;

        return true;
    }

    /** The predicted duration of a rescaling of this cluster */
    public Duration scalingDuration() {
        int completedEventCount = 0;
        Duration totalDuration = Duration.ZERO;
        for (ScalingEvent event : cluster.scalingEvents()) {
            if (event.duration().isEmpty()) continue;
            completedEventCount++;
            totalDuration = totalDuration.plus(event.duration().get());
        }

        if (completedEventCount == 0) { // Use defaults
            if (nodes.clusterSpec().isStateful()) return Duration.ofHours(12);
            return Duration.ofMinutes(10);
        }
        else {
            Duration predictedDuration = totalDuration.dividedBy(completedEventCount);

            // TODO: Remove when we have reliable completion for content clusters
            if (nodes.clusterSpec().isStateful() && predictedDuration.minus(Duration.ofHours(12)).isNegative())
                return Duration.ofHours(12);

            if (predictedDuration.minus(Duration.ofMinutes(5)).isNegative()) return Duration.ofMinutes(5); // minimum
            return predictedDuration;
        }
    }

}
