// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling.Status;

import java.time.Duration;
import java.time.Instant;
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
    public Autoscaling suggest(Application application, Cluster cluster, NodeList clusterNodes) {
        return autoscale(application, cluster, clusterNodes, Limits.empty());
    }

    /**
     * Autoscale a cluster by load. This returns a better allocation (if found) inside the min and max limits.
     *
     * @param clusterNodes the list of all the active nodes in a cluster
     * @return scaling advice for this cluster
     */
    public Autoscaling autoscale(Application application, Cluster cluster, NodeList clusterNodes) {
        return autoscale(application, cluster, clusterNodes, Limits.of(cluster));
    }

    private Autoscaling autoscale(Application application, Cluster cluster, NodeList clusterNodes, Limits limits) {
        ClusterModel clusterModel = new ClusterModel(nodeRepository,
                                                     application,
                                                     clusterNodes.not().retired().clusterSpec(),
                                                     cluster,
                                                     clusterNodes,
                                                     nodeRepository.metricsDb(),
                                                     nodeRepository.clock());
        if (clusterModel.isEmpty()) return Autoscaling.empty();

        if (! limits.isEmpty() && cluster.minResources().equals(cluster.maxResources()))
            return Autoscaling.dontScale(Autoscaling.Status.unavailable, "Autoscaling is not enabled", clusterModel);

        if ( ! clusterModel.isStable(nodeRepository))
            return Autoscaling.dontScale(Status.waiting, "Cluster change in progress", clusterModel);

        var currentAllocation = new AllocatableClusterResources(clusterNodes.not().retired(), nodeRepository);
        Optional<AllocatableClusterResources> bestAllocation =
                allocationOptimizer.findBestAllocation(clusterModel.loadAdjustment(), currentAllocation, clusterModel, limits);
        if (bestAllocation.isEmpty())
            return Autoscaling.dontScale(Status.insufficient, "No allocations are possible within configured limits", clusterModel);

        if (! worthRescaling(currentAllocation.realResources(), bestAllocation.get().realResources())) {
            if (bestAllocation.get().fulfilment() < 0.9999999)
                return Autoscaling.dontScale(Status.insufficient, "Configured limits prevents ideal scaling of this cluster", clusterModel);
            else if ( ! clusterModel.safeToScaleDown() && clusterModel.idealLoad().any(v -> v < 1.0))
                return Autoscaling.dontScale(Status.ideal, "Cooling off before considering to scale down", clusterModel);
            else
                return Autoscaling.dontScale(Status.ideal, "Cluster is ideally scaled (within limits)", clusterModel);
        }

        return Autoscaling.scaleTo(bestAllocation.get().advertisedResources(), clusterModel);
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

}
