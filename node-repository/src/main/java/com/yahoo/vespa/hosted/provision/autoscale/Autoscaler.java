// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling.Status;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/**
 * The autoscaler gives advice about what resources should be allocated to a cluster based on observed behavior.
 *
 * @author bratseth
 */
public class Autoscaler {

    private static final Logger log = Logger.getLogger(Autoscaler.class.getName());

    /** What cost difference is worth a reallocation? */
    private static final double costDifferenceWorthReallocation = 0.1;
    /** What resource difference is worth a reallocation? */
    private static final double resourceIncreaseWorthReallocation = 0.03;
    /** The load increase headroom (as a fraction) we should have before needing to scale up, to decide to scale down */
    static final double headroomRequiredToScaleDown = 0.15;

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
    public List<Autoscaling> suggest(Application application, Cluster cluster, NodeList clusterNodes) {
        var model = model(application, cluster, clusterNodes);
        if (model.isEmpty() || ! model.isStable(nodeRepository)) return List.of();

        var targets = allocationOptimizer.findBestAllocations(model.loadAdjustment(), model, Limits.empty(), false);
        return targets.stream()
                .map(target -> toAutoscaling(target, model))
                .toList();
    }

    /**
     * Autoscale a cluster by load. This returns a better allocation (if found) inside the min and max limits.
     *
     * @param clusterNodes          the list of all the active nodes in a cluster
     * @param enabled               Whether autoscaling is enabled
     * @param logDetails            Whether to log decision details
     * @return scaling advice for this cluster
     */
    public Autoscaling autoscale(Application application, Cluster cluster, NodeList clusterNodes, boolean enabled, boolean logDetails) {
        var limits = Limits.of(cluster);
        var model = model(application, cluster, clusterNodes);
        if (model.isEmpty()) return Autoscaling.empty();

        boolean disabledByUser = !limits.isEmpty() && cluster.minResources().equals(cluster.maxResources());
        boolean disabledByFeatureFlag = !enabled;
        if (disabledByUser)
            return Autoscaling.dontScale(Autoscaling.Status.unavailable, "Autoscaling is not enabled", model);
        if (disabledByFeatureFlag)
            return Autoscaling.dontScale(Autoscaling.Status.unavailable, "Autoscaling is disabled by feature flag", model);

        if ( ! model.isStable(nodeRepository))
            return Autoscaling.dontScale(Status.waiting, "Cluster change in progress", model);

        var loadAdjustment = model.loadAdjustment();
        if (logDetails) {
            log.info("Application: " + application.id().toShortString() + ", loadAdjustment: " +
                     loadAdjustment.toString() + ", ideal " + model.idealLoad() + ", " + model.cpu(nodeRepository.clock().instant()));
        }

        var target = allocationOptimizer.findBestAllocation(loadAdjustment, model, limits, logDetails);

        if (target.isEmpty())
            return Autoscaling.dontScale(Status.insufficient, "No allocations are possible within configured limits", model);

       return toAutoscaling(target.get(), model);
    }

    private ClusterModel model(Application application, Cluster cluster, NodeList clusterNodes) {
        return new ClusterModel(nodeRepository,
                application,
                clusterNodes.not().retired().clusterSpec(),
                cluster,
                clusterNodes,
                new AllocatableResources(clusterNodes.not().retired(), nodeRepository),
                nodeRepository.metricsDb(),
                nodeRepository.clock());
    }

    private Autoscaling toAutoscaling(AllocatableResources target, ClusterModel model) {
        if (target.nodes() == 1)
            return Autoscaling.dontScale(Status.unavailable, "Autoscaling is disabled in single node clusters", model);

        if (! worthRescaling(model.current().realResources(), target.realResources())) {
            if (target.notFulfiled())
                return Autoscaling.dontScale(Status.insufficient, "Cluster cannot be scaled to achieve ideal load", model);
            else if ( ! model.safeToScaleDown() && model.idealLoad().any(v -> v < 1.0))
                return Autoscaling.dontScale(Status.ideal, "Cooling off before considering to scale down", model);
            else
                return Autoscaling.dontScale(Status.ideal, "Cluster is ideally scaled (within configured limits)", model);
        }
        return Autoscaling.scaleTo(target.advertisedResources(), model);
    }

    /** Returns true if it is worthwhile to make the given resource change, false if it is too insignificant */
    public static boolean worthRescaling(ClusterResources from, ClusterResources to) {
        // *Increase* if needed with no regard for cost difference to prevent running out of a resource
        if (meaningfulIncrease(from.totalResources().vcpu(), to.totalResources().vcpu())) return true;
        if (meaningfulIncrease(from.totalResources().memoryGiB(), to.totalResources().memoryGiB())) return true;
        if (meaningfulIncrease(from.totalResources().diskGb(), to.totalResources().diskGb())) return true;

        // Otherwise, only *decrease* if
        // - cost is reduced meaningfully
        // - the new resources won't be so much smaller that a small fluctuation in load will cause an increase
        return ! similar(from.cost(), to.cost(), costDifferenceWorthReallocation);
    }

    public static boolean meaningfulIncrease(double from, double to) {
        return from < to && ! similar(from, to, resourceIncreaseWorthReallocation);
    }

    private static boolean similar(double r1, double r2, double threshold) {
        return Math.abs(r1 - r2) / (( r1 + r2) / 2) < threshold;
    }

    static Duration maxScalingWindow() {
        return Duration.ofHours(48);
    }

}
