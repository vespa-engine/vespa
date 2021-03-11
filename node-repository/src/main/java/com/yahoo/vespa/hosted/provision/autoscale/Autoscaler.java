// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The autoscaler gives advice about what resources should be allocated to a cluster based on observed behavior.
 *
 * @author bratseth
 */
public class Autoscaler {

    /** What cost difference is worth a reallocation? */
    private static final double costDifferenceWorthReallocation = 0.1;
    /** What resource difference is worth a reallocation? */
    private static final double resourceDifferenceWorthReallocation = 0.1;

    private final MetricsDb metricsDb;
    private final NodeRepository nodeRepository;
    private final AllocationOptimizer allocationOptimizer;

    public Autoscaler(MetricsDb metricsDb, NodeRepository nodeRepository) {
        this.metricsDb = metricsDb;
        this.nodeRepository = nodeRepository;
        this.allocationOptimizer = new AllocationOptimizer(nodeRepository);
    }

    /**
     * Suggest a scaling of a cluster. This returns a better allocation (if found)
     * without taking min and max limits into account.
     *
     * @param clusterNodes the list of all the active nodes in a cluster
     * @return scaling advice for this cluster
     */
    public Advice suggest(Application application, Cluster cluster, NodeList clusterNodes) {
        return autoscale(application, cluster, clusterNodes, Limits.empty());
    }

    /**
     * Autoscale a cluster by load. This returns a better allocation (if found) inside the min and max limits.
     *
     * @param clusterNodes the list of all the active nodes in a cluster
     * @return scaling advice for this cluster
     */
    public Advice autoscale(Application application, Cluster cluster, NodeList clusterNodes) {
        if (cluster.minResources().equals(cluster.maxResources())) return Advice.none("Autoscaling is not enabled");
        return autoscale(application, cluster, clusterNodes, Limits.of(cluster));
    }

    private Advice autoscale(Application application, Cluster cluster, NodeList clusterNodes, Limits limits) {
        if ( ! stable(clusterNodes, nodeRepository))
            return Advice.none("Cluster change in progress");

        Duration scalingWindow = cluster.scalingDuration(clusterNodes.clusterSpec());
        if (scaledIn(scalingWindow, cluster))
            return Advice.dontScale("Won't autoscale now: Less than " + scalingWindow + " since last resource change");

        var clusterNodesTimeseries = new ClusterNodesTimeseries(scalingWindow, cluster, clusterNodes, metricsDb);
        var currentAllocation = new AllocatableClusterResources(clusterNodes.asList(), nodeRepository, cluster.exclusive());

        int measurementsPerNode = clusterNodesTimeseries.measurementsPerNode();
        if  (measurementsPerNode < minimumMeasurementsPerNode(scalingWindow))
            return Advice.none("Collecting more data before making new scaling decisions: Need to measure for " +
                               scalingWindow + " since the last resource change completed");

        int nodesMeasured = clusterNodesTimeseries.nodesMeasured();
        if (nodesMeasured != clusterNodes.size())
            return Advice.none("Collecting more data before making new scaling decisions: " +
                               "Have measurements from " + nodesMeasured + " nodes, but require from " + clusterNodes.size());


        var scalingDuration = cluster.scalingDuration(clusterNodes.clusterSpec());
        var clusterTimeseries = metricsDb.getClusterTimeseries(application.id(), cluster.id());
        var target = ResourceTarget.idealLoad(scalingDuration,
                                              clusterTimeseries,
                                              clusterNodesTimeseries,
                                              currentAllocation,
                                              application);

        Optional<AllocatableClusterResources> bestAllocation =
                allocationOptimizer.findBestAllocation(target, currentAllocation, limits);
        if (bestAllocation.isEmpty())
            return Advice.dontScale("No allocation improvements are possible within configured limits");

        if (similar(bestAllocation.get().realResources(), currentAllocation.realResources()))
            return Advice.dontScale("Cluster is ideally scaled within configured limits");

        if (isDownscaling(bestAllocation.get(), currentAllocation) && scaledIn(scalingWindow.multipliedBy(3), cluster))
            return Advice.dontScale("Waiting " + scalingWindow.multipliedBy(3) +
                                    " since the last change before reducing resources");

        return Advice.scaleTo(bestAllocation.get().advertisedResources());
    }

    /** Returns true if both total real resources and total cost are similar */
    public static boolean similar(ClusterResources a, ClusterResources b) {
        return similar(a.cost(), b.cost(), costDifferenceWorthReallocation) &&
               similar(a.totalResources().vcpu(), b.totalResources().vcpu(), resourceDifferenceWorthReallocation) &&
               similar(a.totalResources().memoryGb(), b.totalResources().memoryGb(), resourceDifferenceWorthReallocation) &&
               similar(a.totalResources().diskGb(), b.totalResources().diskGb(), resourceDifferenceWorthReallocation);
    }

    private static boolean similar(double r1, double r2, double threshold) {
        return Math.abs(r1 - r2) / (( r1 + r2) / 2) < threshold;
    }

    /** Returns true if this reduces total resources in any dimension */
    private boolean isDownscaling(AllocatableClusterResources target, AllocatableClusterResources current) {
        NodeResources targetTotal = target.advertisedResources().totalResources();
        NodeResources currentTotal = current.advertisedResources().totalResources();
        return ! targetTotal.justNumbers().satisfies(currentTotal.justNumbers());
    }

    private boolean scaledIn(Duration delay, Cluster cluster) {
        return cluster.lastScalingEvent().map(event -> event.at()).orElse(Instant.MIN)
                      .isAfter(nodeRepository.clock().instant().minus(delay));
    }

    static Duration maxScalingWindow() {
        return Duration.ofHours(48);
    }

    /** Returns the minimum measurements per node (average) we require to give autoscaling advice.*/
    private int minimumMeasurementsPerNode(Duration scalingWindow) {
        // Measurements are ideally taken every minute, but no guarantees
        // (network, nodes may be down, collecting is single threaded and may take longer than 1 minute to complete).
        // Since the metric window is 5 minutes, we won't really improve from measuring more often.
        long minimumMeasurements = scalingWindow.toMinutes() / 5;
        minimumMeasurements = Math.round(0.8 * minimumMeasurements); // Allow 20% metrics collection blackout
        if (minimumMeasurements < 1) minimumMeasurements = 1;
        return (int)minimumMeasurements;
    }

    public static boolean stable(NodeList nodes, NodeRepository nodeRepository) {
        // The cluster is processing recent changes
        if (nodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                            node.allocation().get().membership().retired() ||
                                            node.allocation().get().isRemovable()))
            return false;

        // A deployment is ongoing
        if (nodeRepository.nodes().list(Node.State.reserved).owner(nodes.first().get().allocation().get().owner()).size() > 0)
            return false;

        return true;
    }

    public static class Advice {

        private final boolean present;
        private final Optional<ClusterResources> target;
        private final String reason;

        private Advice(Optional<ClusterResources> target, boolean present, String reason) {
            this.target = target;
            this.present = present;
            this.reason = Objects.requireNonNull(reason);
        }

        /**
         * Returns the autoscaling target that should be set by this advice.
         * This is empty if the advice is to keep the current allocation.
         */
        public Optional<ClusterResources> target() { return target; }

        /** True if this does not provide any advice */
        public boolean isEmpty() { return ! present; }

        /** True if this provides advice (which may be to keep the current allocation) */
        public boolean isPresent() { return present; }

        /** The reason for this advice */
        public String reason() { return reason; }

        private static Advice none(String reason) { return new Advice(Optional.empty(), false, reason); }
        private static Advice dontScale(String reason) { return new Advice(Optional.empty(), true, reason); }
        private static Advice scaleTo(ClusterResources target) {
            return new Advice(Optional.of(target), true, "Scheduled scaling to " + target + " due to load changes");
        }

        @Override
        public String toString() {
            return "autoscaling advice: " +
                   (present ? (target.isPresent() ? "Scale to " + target.get() : "Don't scale") : " None");
        }

    }

}
