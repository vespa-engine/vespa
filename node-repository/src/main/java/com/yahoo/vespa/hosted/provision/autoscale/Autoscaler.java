// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;

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

    /** What cost difference factor is worth a reallocation? */
    private static final double costDifferenceWorthReallocation = 0.1;
    /** What difference factor for a resource is worth a reallocation? */
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
    public Advice suggest(Cluster cluster, NodeList clusterNodes) {
        return autoscale(cluster, clusterNodes, Limits.empty(), cluster.exclusive());
    }

    /**
     * Autoscale a cluster by load. This returns a better allocation (if found) inside the min and max limits.
     *
     * @param clusterNodes the list of all the active nodes in a cluster
     * @return scaling advice for this cluster
     */
    public Advice autoscale(Cluster cluster, NodeList clusterNodes) {
        if (cluster.minResources().equals(cluster.maxResources())) return Advice.none("Autoscaling is disabled"); // Shortcut
        return autoscale(cluster, clusterNodes, Limits.of(cluster), cluster.exclusive());
    }

    private Advice autoscale(Cluster cluster, NodeList clusterNodes, Limits limits, boolean exclusive) {
        if ( ! stable(clusterNodes, nodeRepository))
            return Advice.none("Cluster change in progress");

        AllocatableClusterResources currentAllocation =
                new AllocatableClusterResources(clusterNodes.asList(), nodeRepository, cluster.exclusive());

        ClusterTimeseries clusterTimeseries = new ClusterTimeseries(cluster, clusterNodes, metricsDb, nodeRepository);

        int measurementsPerNode = clusterTimeseries.measurementsPerNode();
        if  (measurementsPerNode < minimumMeasurementsPerNode(clusterNodes.clusterSpec()))
            return Advice.none("Collecting more data before making new scaling decisions" +
                               " (as " + measurementsPerNode + " data points per node but need " +
                               minimumMeasurementsPerNode(clusterNodes.clusterSpec()) + ")");

        int nodesMeasured = clusterTimeseries.nodesMeasured();
        if (nodesMeasured != clusterNodes.size())
            return Advice.none("Collecting more data before making new scaling decisions" +
                               "(has measurements from " + nodesMeasured + " but need from " + clusterNodes.size() + ")");

        double cpuLoad    = clusterTimeseries.averageLoad(Resource.cpu);
        double memoryLoad = clusterTimeseries.averageLoad(Resource.memory);
        double diskLoad   = clusterTimeseries.averageLoad(Resource.disk);

        var target = ResourceTarget.idealLoad(cpuLoad, memoryLoad, diskLoad, currentAllocation);

        Optional<AllocatableClusterResources> bestAllocation =
                allocationOptimizer.findBestAllocation(target, currentAllocation, limits, exclusive);
        if (bestAllocation.isEmpty())
            return Advice.dontScale("No allocation changes are possible within configured limits");

        if (similar(bestAllocation.get(), currentAllocation))
            return Advice.dontScale("Cluster is ideally scaled (within configured limits)");

        Duration scalingWindow = scalingWindow(clusterNodes.clusterSpec(), cluster);
        if (scaledIn(scalingWindow, cluster))
            return Advice.dontScale("Won't autoscale now: Less than " + scalingWindow + " since last rescaling");
        if (isDownscaling(bestAllocation.get(), currentAllocation) && scaledIn(scalingWindow.multipliedBy(3), cluster))
            return Advice.dontScale("Waiting " + scalingWindow.multipliedBy(3) + " since last rescaling before reducing resources");

        return Advice.scaleTo(bestAllocation.get().toAdvertisedClusterResources());
    }

    /** Returns true if both total real resources and total cost are similar */
    private boolean similar(AllocatableClusterResources a, AllocatableClusterResources b) {
        return similar(a.cost(), b.cost(), costDifferenceWorthReallocation) &&
               similar(a.realResources().vcpu() * a.nodes(),
                       b.realResources().vcpu() * b.nodes(), resourceDifferenceWorthReallocation) &&
               similar(a.realResources().memoryGb() * a.nodes(),
                       b.realResources().memoryGb() * b.nodes(), resourceDifferenceWorthReallocation) &&
               similar(a.realResources().diskGb() * a.nodes(),
                       b.realResources().diskGb() * b.nodes(), resourceDifferenceWorthReallocation);
    }

    private boolean similar(double r1, double r2, double threshold) {
        return Math.abs(r1 - r2) / (( r1 + r2) / 2) < threshold;
    }

    /** Returns true if this reduces total resources in any dimension */
    private boolean isDownscaling(AllocatableClusterResources target, AllocatableClusterResources current) {
        NodeResources targetTotal = target.toAdvertisedClusterResources().totalResources();
        NodeResources currentTotal = current.toAdvertisedClusterResources().totalResources();
        return ! targetTotal.justNumbers().satisfies(currentTotal.justNumbers());
    }

    private boolean scaledIn(Duration delay, Cluster cluster) {
        return cluster.lastScalingEvent().map(event -> event.at()).orElse(Instant.MIN)
                      .isAfter(nodeRepository.clock().instant().minus(delay));
    }

    /** The duration of the window we need to consider to make a scaling decision. See also minimumMeasurementsPerNode */
    static Duration scalingWindow(ClusterSpec clusterSpec, Cluster cluster) {
        int completedEventCount = 0;
        Duration totalDuration = Duration.ZERO;
        for (ScalingEvent event : cluster.scalingEvents()) {
            if (event.duration().isEmpty()) continue;
            completedEventCount++;
            totalDuration = totalDuration.plus(event.duration().get());
        }

        if (completedEventCount == 0) { // Use defaults
            if (clusterSpec.isStateful()) return Duration.ofHours(12);
            return Duration.ofMinutes(30);
        }
        else {
            Duration predictedDuration = totalDuration.dividedBy(completedEventCount);

            // TODO: Remove when we have reliable completion for content clusters
            if (clusterSpec.isStateful() && predictedDuration.minus(Duration.ofHours(12)).isNegative())
                return Duration.ofHours(12);

            if (predictedDuration.minus(Duration.ofMinutes(5)).isNegative()) return Duration.ofMinutes(5); // minimum
            return predictedDuration;
        }
    }

    static Duration maxScalingWindow() {
        return Duration.ofHours(48);
    }

    /** Measurements are currently taken once a minute. See also scalingWindow */
    static int minimumMeasurementsPerNode(ClusterSpec cluster) {
        if (cluster.isStateful()) return 60;
        return 4;
    }

    public static boolean stable(NodeList nodes, NodeRepository nodeRepository) {
        // The cluster is processing recent changes
        if (nodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                            node.allocation().get().membership().retired() ||
                                            node.allocation().get().isRemovable()))
            return false;

        // A deployment is ongoing
        if (nodeRepository.getNodes(nodes.first().get().allocation().get().owner(), Node.State.reserved).size() > 0)
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
            return new Advice(Optional.of(target), true, "Scaling due to load changes");
        }

        @Override
        public String toString() {
            return "autoscaling advice: " +
                   (present ? (target.isPresent() ? "Scale to " + target.get() : "Don't scale") : " None");
        }

    }

}
