// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.AutoscalingStatus;
import com.yahoo.vespa.hosted.provision.applications.AutoscalingStatus.Status;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.time.Duration;
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
    private static final double resourceDifferenceWorthReallocation = 0.03;

    private final NodeRepository nodeRepository;
    private final AllocationOptimizer allocationOptimizer;

    public Autoscaler(NodeRepository nodeRepository) {
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
        if (cluster.minResources().equals(cluster.maxResources()))
            return Advice.none(Status.unavailable, "Autoscaling is not enabled");
        return autoscale(application, cluster, clusterNodes, Limits.of(cluster));
    }

    private Advice autoscale(Application application, Cluster cluster, NodeList clusterNodes, Limits limits) {
        ClusterModel clusterModel = new ClusterModel(application,
                                                     clusterNodes.clusterSpec(),
                                                     cluster,
                                                     clusterNodes,
                                                     nodeRepository.metricsDb(),
                                                     nodeRepository.clock());

        if ( ! clusterIsStable(clusterNodes, nodeRepository))
            return Advice.none(Status.waiting, "Cluster change in progress");

        var currentAllocation = new AllocatableClusterResources(clusterNodes, nodeRepository);
        Optional<AllocatableClusterResources> bestAllocation =
                allocationOptimizer.findBestAllocation(clusterModel.loadAdjustment(), currentAllocation, clusterModel, limits);
        if (bestAllocation.isEmpty())
            return Advice.dontScale(Status.insufficient, "No allocations are possible within configured limits");

        if (! worthRescaling(currentAllocation.realResources(), bestAllocation.get().realResources())) {
            if (bestAllocation.get().fulfilment() < 1)
                return Advice.dontScale(Status.insufficient, "Configured limits prevents better scaling of this cluster");
            else
                return Advice.dontScale(Status.ideal, "Cluster is ideally scaled");
        }

        return Advice.scaleTo(bestAllocation.get().advertisedResources());
    }

    public static boolean clusterIsStable(NodeList clusterNodes, NodeRepository nodeRepository) {
        // The cluster is processing recent changes
        if (clusterNodes.stream().anyMatch(node -> node.status().wantToRetire() ||
                                                   node.allocation().get().membership().retired() ||
                                                   node.allocation().get().removable()))
            return false;

        // A deployment is ongoing
        if (nodeRepository.nodes().list(Node.State.reserved).owner(clusterNodes.first().get().allocation().get().owner()).size() > 0)
            return false;

        return true;
    }

    /** Returns true if it is worthwhile to make the given resource change, false if it is too insignificant */
    public static boolean worthRescaling(ClusterResources from, ClusterResources to) {
        // *Increase* if needed with no regard for cost difference to prevent running out of a resource
        if (meaningfulIncrease(from.totalResources().vcpu(), to.totalResources().vcpu())) return true;
        if (meaningfulIncrease(from.totalResources().memoryGb(), to.totalResources().memoryGb())) return true;
        if (meaningfulIncrease(from.totalResources().diskGb(), to.totalResources().diskGb())) return true;

        // Otherwise, only *decrease* if it reduces cost meaningfully
        return ! similar(from.cost(), to.cost(), costDifferenceWorthReallocation);
    }

    public static boolean meaningfulIncrease(double from, double to) {
        return from < to && ! similar(from, to, resourceDifferenceWorthReallocation);
    }

    private static boolean similar(double r1, double r2, double threshold) {
        return Math.abs(r1 - r2) / (( r1 + r2) / 2) < threshold;
    }

    static Duration maxScalingWindow() {
        return Duration.ofHours(48);
    }

    public static class Advice {

        private final boolean present;
        private final Optional<ClusterResources> target;
        private final AutoscalingStatus reason;

        private Advice(Optional<ClusterResources> target, boolean present, AutoscalingStatus reason) {
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
        public AutoscalingStatus reason() { return reason; }

        private static Advice none(Status status, String description) {
            return new Advice(Optional.empty(), false, new AutoscalingStatus(status, description));
        }

        private static Advice dontScale(Status status, String description) {
            return new Advice(Optional.empty(), true, new AutoscalingStatus(status, description));
        }

        private static Advice scaleTo(ClusterResources target) {
            return new Advice(Optional.of(target), true,
                              new AutoscalingStatus(AutoscalingStatus.Status.rescaling,
                                                    "Rescaling initiated due to load changes"));
        }

        @Override
        public String toString() {
            return "autoscaling advice: " +
                   (present ? (target.isPresent() ? "Scale to " + target.get() : "Don't scale") : "None");
        }

    }

}
