// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A cluster with its associated metrics which allows prediction about its future behavior.
 * For single-threaded, short-term usage.
 *
 * @author bratseth
 */
public class ClusterModel {

    private static final Logger log = Logger.getLogger(ClusterModel.class.getName());

    /** Containers typically use more cpu right after generation change, so discard those metrics */
    public static final Duration warmupDuration = Duration.ofMinutes(5);

    static final double idealQueryCpuLoad = 0.8;
    static final double idealWriteCpuLoad = 0.95;
    static final double idealMemoryLoad = 0.65;
    static final double idealContainerDiskLoad = 0.95;
    static final double idealContentDiskLoad = 0.6;

    // When a query is issued on a node the cost is the sum of a fixed cost component and a cost component
    // proportional to document count. We must account for this when comparing configurations with more or fewer nodes.
    // TODO: Measure this, and only take it into account with queries
    private static final double fixedCpuCostFraction = 0.1;

    private final Application application;
    private final ClusterSpec clusterSpec;
    private final Cluster cluster;
    /** The current nodes of this cluster, or empty if this models a new cluster not yet deployed */
    private final NodeList nodes;
    private final Clock clock;
    private final Duration scalingDuration;
    private final ClusterTimeseries clusterTimeseries;
    private final ClusterNodesTimeseries nodeTimeseries;

    // Lazily initialized members
    private Double queryFractionOfMax = null;
    private Double maxQueryGrowthRate = null;

    public ClusterModel(Application application,
                        ClusterSpec clusterSpec,
                        Cluster cluster,
                        NodeList clusterNodes,
                        MetricsDb metricsDb,
                        Clock clock) {
        this.application = application;
        this.clusterSpec = clusterSpec;
        this.cluster = cluster;
        this.nodes = clusterNodes;
        this.clock = clock;
        this.scalingDuration = computeScalingDuration(cluster, clusterSpec);
        this.clusterTimeseries = metricsDb.getClusterTimeseries(application.id(), cluster.id());
        this.nodeTimeseries = new ClusterNodesTimeseries(scalingDuration(), cluster, nodes, metricsDb);
    }

    /** For testing */
    ClusterModel(Application application,
                 ClusterSpec clusterSpec,
                 Cluster cluster,
                 Clock clock,
                 Duration scalingDuration,
                 ClusterTimeseries clusterTimeseries,
                 ClusterNodesTimeseries nodeTimeseries) {
        this.application = application;
        this.clusterSpec = clusterSpec;
        this.cluster = cluster;
        this.nodes = NodeList.of();
        this.clock = clock;

        this.scalingDuration = scalingDuration;
        this.clusterTimeseries = clusterTimeseries;
        this.nodeTimeseries = nodeTimeseries;
    }

    public Application application() { return application; }
    public ClusterSpec clusterSpec() { return clusterSpec; }
    public Cluster cluster() { return cluster; }

    /** Returns the relative load adjustment that should be made to this cluster given available measurements. */
    public Load loadAdjustment() {
        if (nodeTimeseries().isEmpty()) return Load.one();

        Load adjustment = peakLoad().divide(idealLoad());
        if (! safeToScaleDown())
            adjustment = adjustment.map(v -> v < 1 ? 1 : v);
        return adjustment;
    }

    /** Are we in a position to make decisions to scale down at this point? */
    private boolean safeToScaleDown() {
        if (hasScaledIn(scalingDuration().multipliedBy(3))) return false;
        if (nodeTimeseries().measurementsPerNode() < 4) return false;
        if (nodeTimeseries().nodesMeasured() != nodeCount()) return false;
        return true;
    }

    private boolean hasScaledIn(Duration period) {
        return cluster.lastScalingEvent().map(event -> event.at()).orElse(Instant.MIN)
                      .isAfter(clock.instant().minus(period));
    }

    /** Returns the predicted duration of a rescaling of this cluster */
    public Duration scalingDuration() { return scalingDuration; }

    public ClusterNodesTimeseries nodeTimeseries() { return nodeTimeseries; }

    public ClusterTimeseries clusterTimeseries() { return clusterTimeseries; }

    /**
     * Returns the predicted max query growth rate per minute as a fraction of the average traffic
     * in the scaling window
     */
    public double maxQueryGrowthRate() {
        if (maxQueryGrowthRate != null) return maxQueryGrowthRate;
        return maxQueryGrowthRate = clusterTimeseries().maxQueryGrowthRate(scalingDuration(), clock);
    }

    /** Returns the average query rate in the scaling window as a fraction of the max observed query rate */
    public double queryFractionOfMax() {
        if (queryFractionOfMax != null) return queryFractionOfMax;
        return queryFractionOfMax = clusterTimeseries().queryFractionOfMax(scalingDuration(), clock);
    }

    /** Returns the average of the last load measurement from each node. */
    public Load currentLoad() { return nodeTimeseries().currentLoad(); }

    /** Returns the average of all load measurements from all nodes*/
    public Load averageLoad() { return nodeTimeseries().averageLoad(); }

    /** Returns the average of the peak load measurement in each dimension, from each node. */
    public Load peakLoad() { return nodeTimeseries().peakLoad(); }

    /** The number of nodes this cluster has, or will have if not deployed yet. */
    // TODO: Make this the deployed, not current count
    public int nodeCount() {
        if ( ! nodes.isEmpty()) return (int)nodes.stream().count();
        return cluster.minResources().nodes();
    }

    /** The number of groups this cluster has, or will have if not deployed yet. */
    // TODO: Make this the deployed, not current count
    public int groupCount() {
        if ( ! nodes.isEmpty()) return (int)nodes.stream().mapToInt(node -> node.allocation().get().membership().cluster().group().get().index()).distinct().count();
        return cluster.minResources().groups();
    }

    public int groupSize() {
        // ceil: If the division does not produce a whole number we assume some node is missing
        return (int)Math.ceil((double)nodeCount() / groupCount());
    }

    /** Returns the relative load adjustment accounting for redundancy in this. */
    public Load redundancyAdjustment() {
        return loadWith(nodeCount(), groupCount());
    }

    /**
     * Returns the relative load adjustment accounting for redundancy given these nodes+groups
     * relative to node nodes+groups in this.
     */
    public Load loadWith(int trueNodes, int trueGroups) {
        int nodes = nodesAdjustedForRedundancy(trueNodes, trueGroups);
        int groups = groupsAdjustedForRedundancy(trueNodes, trueGroups);
        if (clusterSpec().type() == ClusterSpec.Type.content) { // load scales with node share of content
            int groupSize = nodes / groups;

            // Cpu: Query cpu scales with cluster size, write cpu scales with group size
            // Memory and disk: Scales with group size

            // The fixed cost portion of cpu does not scale with changes to the node count
            double queryCpuPerGroup = fixedCpuCostFraction + (1 - fixedCpuCostFraction) * groupSize() / groupSize;

            double queryCpu = queryCpuPerGroup * groupCount() / groups;
            double writeCpu = (double)groupSize() / groupSize;
            return new Load(queryCpuFraction() * queryCpu + (1 - queryCpuFraction()) * writeCpu,
                            (double)groupSize() / groupSize,
                            (double)groupSize() / groupSize);
        }
        else {
            return new Load((double)nodeCount() / nodes, 1, 1);
        }
    }

    /**
     * Returns the ideal load across the nodes of this sich that each node will be at ideal load
     * if one of  the nodes go down.
     */
    public Load idealLoad() {
        return new Load(idealCpuLoad(), idealMemoryLoad, idealDiskLoad()).divide(redundancyAdjustment());
    }

    public int nodesAdjustedForRedundancy(int nodes, int groups) {
        int groupSize = (int)Math.ceil((double)nodes / groups);
        return nodes > 1 ? (groups == 1 ? nodes - 1 : nodes - groupSize) : nodes;
    }

    public int groupsAdjustedForRedundancy(int nodes, int groups) {
        return nodes > 1 ? (groups == 1 ? 1 : groups - 1) : groups;
    }

    /** Ideal cpu load must take the application traffic fraction into account. */
    private double idealCpuLoad() {
        double queryCpuFraction = queryCpuFraction();

        // What's needed to have headroom for growth during scale-up as a fraction of current resources?
        double growthRateHeadroom = 1 + maxQueryGrowthRate() * scalingDuration().toMinutes();
        // Cap headroom at 10% above the historical observed peak
        if (queryFractionOfMax() != 0)
            growthRateHeadroom = Math.min(growthRateHeadroom, 1 / queryFractionOfMax() + 0.1);

        // How much headroom is needed to handle sudden arrival of additional traffic due to another zone going down?
        double trafficShiftHeadroom;
        if (application.status().maxReadShare() == 0) // No traffic fraction data
            trafficShiftHeadroom = 2.0; // assume we currently get half of the global share of traffic
        else if (application.status().currentReadShare() == 0)
            trafficShiftHeadroom = 1/application.status().maxReadShare();
        else
            trafficShiftHeadroom = application.status().maxReadShare() / application.status().currentReadShare();
        trafficShiftHeadroom = Math.min(trafficShiftHeadroom, 1/application.status().maxReadShare());

        // Assumptions: 1) Write load is not organic so we should not grow to handle more.
        //                 (TODO: But allow applications to set their target write rate and size for that)
        //              2) Write load does not change in BCP scenarios.
        return queryCpuFraction * 1/growthRateHeadroom * 1/trafficShiftHeadroom * idealQueryCpuLoad +
               (1 - queryCpuFraction) * idealWriteCpuLoad;
    }

    /** The estimated fraction of cpu usage which goes to processing queries vs. writes */
    public double queryCpuFraction() {
        OptionalDouble queryRate = clusterTimeseries().queryRate(scalingDuration(), clock);
        OptionalDouble writeRate = clusterTimeseries().writeRate(scalingDuration(), clock);
        if (queryRate.orElse(0) == 0 && writeRate.orElse(0) == 0) return queryCpuFraction(0.5);
        return queryCpuFraction(queryRate.orElse(0) / (queryRate.orElse(0) + writeRate.orElse(0)));
    }

    private double queryCpuFraction(double queryRateFraction) {
        double relativeQueryCost = 9; // How much more expensive are queries than writes? TODO: Measure
        double writeFraction = 1 - queryRateFraction;
        return queryRateFraction * relativeQueryCost / (queryRateFraction * relativeQueryCost + writeFraction);
    }

    private static Duration computeScalingDuration(Cluster cluster, ClusterSpec clusterSpec) {
        int completedEventCount = 0;
        Duration totalDuration = Duration.ZERO;
        for (ScalingEvent event : cluster.scalingEvents()) {
            if (event.duration().isEmpty()) continue;
            completedEventCount++;
            // Assume we have missed timely recording completion if it is longer than 4 days
            totalDuration = totalDuration.plus(maximum(Duration.ofDays(4), event.duration().get()));
        }
        if (completedEventCount == 0) { // Use defaults
            if (clusterSpec.isStateful()) return Duration.ofHours(12);
            return Duration.ofMinutes(10);
        }
        else {
            Duration predictedDuration = totalDuration.dividedBy(completedEventCount);

            if ( clusterSpec.isStateful() ) // TODO: Remove when we have reliable completion for content clusters
                predictedDuration = minimum(Duration.ofHours(12), predictedDuration);

            predictedDuration = minimum(Duration.ofMinutes(5), predictedDuration);

            return predictedDuration;
        }
    }

    private static Duration minimum(Duration smallestAllowed, Duration duration) {
        if (duration.minus(smallestAllowed).isNegative())
            return smallestAllowed;
        return duration;
    }

    private static Duration maximum(Duration largestAllowed, Duration duration) {
        if ( ! duration.minus(largestAllowed).isNegative())
            return largestAllowed;
        return duration;
    }

    private double idealDiskLoad() {
        // Stateless clusters are not expected to consume more disk over time -
        // if they do it is due to logs which will be rotated away right before the disk is full
        return clusterSpec.isStateful() ? idealContentDiskLoad : idealContainerDiskLoad;
    }

    /**
     * Create a cluster model if possible and logs a warning and returns empty otherwise.
     * This is useful in cases where it's possible to continue without the cluser model,
     * as QuestDb is known to temporarily fail during reading of data.
     */
    public static Optional<ClusterModel> create(Application application,
                                                ClusterSpec clusterSpec,
                                                Cluster cluster,
                                                NodeList clusterNodes,
                                                MetricsDb metricsDb,
                                                Clock clock) {
        try {
            return Optional.of(new ClusterModel(application, clusterSpec, cluster, clusterNodes, metricsDb, clock));
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Failed creating a cluster model for " + application + " " + cluster, e);
            return Optional.empty();
        }
    }

}
