// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The autoscaler makes decisions about the flavor and node count that should be allocated to a cluster
 * based on observed behavior.
 *
 * @author bratseth
 */
public class Autoscaler {

    protected final Logger log = Logger.getLogger(this.getClass().getName());

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
    public Advice suggest(Cluster cluster, List<Node> clusterNodes) {
        return autoscale(cluster, clusterNodes, Limits.empty(), cluster.exclusive());
    }

    /**
     * Autoscale a cluster by load. This returns a better allocation (if found) inside the min and max limits.
     *
     * @param clusterNodes the list of all the active nodes in a cluster
     * @return scaling advice for this cluster
     */
    public Advice autoscale(Cluster cluster, List<Node> clusterNodes) {
        if (cluster.minResources().equals(cluster.maxResources())) return Advice.none(); // Shortcut
        return autoscale(cluster, clusterNodes, Limits.of(cluster), cluster.exclusive());
    }

    private Advice autoscale(Cluster cluster, List<Node> clusterNodes, Limits limits, boolean exclusive) {
        if (unstable(clusterNodes, nodeRepository)) {
            log.fine(() -> "Unstable - Advice.none " + cluster.toString());
            return Advice.none();
        }

        AllocatableClusterResources currentAllocation = new AllocatableClusterResources(clusterNodes, nodeRepository, cluster.exclusive());

        ClusterTimeseries clusterTimeseries = new ClusterTimeseries(cluster, clusterNodes, metricsDb, nodeRepository);

        Optional<Double> cpuLoad    = clusterTimeseries.averageLoad(Resource.cpu);
        Optional<Double> memoryLoad = clusterTimeseries.averageLoad(Resource.memory);
        Optional<Double> diskLoad   = clusterTimeseries.averageLoad(Resource.disk);
        if (cpuLoad.isEmpty() || memoryLoad.isEmpty() || diskLoad.isEmpty()) {
            log.fine(() -> "Missing average load - Advice.none  " + cluster.toString());
            return Advice.none();
        }
        var target = ResourceTarget.idealLoad(cpuLoad.get(), memoryLoad.get(), diskLoad.get(), currentAllocation);

        Optional<AllocatableClusterResources> bestAllocation =
                allocationOptimizer.findBestAllocation(target, currentAllocation, limits, exclusive);
        if (bestAllocation.isEmpty()) {
            log.fine(() -> "bestAllocation.isEmpty - Advice.dontScale " + cluster.toString());
            return Advice.dontScale();
        }
        if (similar(bestAllocation.get(), currentAllocation)) {
            log.fine(() -> "Current allocation similar - Advice.dontScale " + cluster.toString());
            return Advice.dontScale();
        }
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

    /** The duration of the window we need to consider to make a scaling decision. See also minimumMeasurementsPerNode */
    static Duration scalingWindow(ClusterSpec.Type clusterType) {
        if (clusterType.isContent()) return Duration.ofHours(12);
        return Duration.ofHours(1);
    }

    static Duration maxScalingWindow() {
        return Duration.ofHours(12);
    }

    /** Measurements are currently taken once a minute. See also scalingWindow */
    static int minimumMeasurementsPerNode(ClusterSpec.Type clusterType) {
        if (clusterType.isContent()) return 60;
        return 20;
    }

    public static boolean unstable(List<Node> nodes, NodeRepository nodeRepository) {
        // The cluster is processing recent changes
        if (nodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                            node.allocation().get().membership().retired() ||
                                            node.allocation().get().isRemovable()))
            return true;

        // A deployment is ongoing
        if (nodeRepository.getNodes(nodes.get(0).allocation().get().owner(), Node.State.reserved).size() > 0)
            return true;

        return false;
    }

    public static class Advice {

        private final boolean present;
        private final Optional<ClusterResources> target;

        private Advice(Optional<ClusterResources> target, boolean present) {
            this.target = target;
            this.present = present;
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

        private static Advice none() { return new Advice(Optional.empty(), false); }
        private static Advice dontScale() { return new Advice(Optional.empty(), true); }
        private static Advice scaleTo(ClusterResources target) { return new Advice(Optional.of(target), true); }

    }

}
