// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;

import java.time.Clock;
import java.time.Duration;
import java.util.OptionalDouble;

/**
 * A cluster with its associated metrics which allows prediction about its future behavior.
 * For single-threaded, short-term usage.
 *
 * @author bratseth
 */
public class ClusterModel {

    static final double idealQueryCpuLoad = 0.8;
    static final double idealWriteCpuLoad = 0.95;
    static final double idealMemoryLoad = 0.7;
    static final double idealDiskLoad = 0.6;

    private final Application application;
    private final Cluster cluster;
    /** The current nodes of this cluster, or empty if this models a new cluster not yet deployed */
    private final NodeList nodes;
    private final MetricsDb metricsDb;
    private final Clock clock;
    private final Duration scalingDuration;

    // Lazily initialized members
    private Double queryFractionOfMax = null;
    private Double maxQueryGrowthRate = null;
    private ClusterNodesTimeseries nodeTimeseries = null;
    private ClusterTimeseries clusterTimeseries = null;

    public ClusterModel(Application application,
                        Cluster cluster,
                        ClusterSpec clusterSpec,
                        NodeList clusterNodes,
                        MetricsDb metricsDb,
                        Clock clock) {
        this.application = application;
        this.cluster = cluster;
        this.nodes = clusterNodes;
        this.metricsDb = metricsDb;
        this.clock = clock;
        this.scalingDuration = computeScalingDuration(cluster, clusterSpec);
    }

    /** For testing */
    ClusterModel(Application application,
                 Cluster cluster,
                 Clock clock,
                 Duration scalingDuration,
                 ClusterTimeseries clusterTimeseries) {
        this.application = application;
        this.cluster = cluster;
        this.nodes = null;
        this.metricsDb = null;
        this.clock = clock;

        this.scalingDuration = scalingDuration;
        this.clusterTimeseries = clusterTimeseries;
    }

    /** Returns the predicted duration of a rescaling of this cluster */
    public Duration scalingDuration() { return scalingDuration; }

    public ClusterNodesTimeseries nodeTimeseries() {
        if (nodeTimeseries != null) return nodeTimeseries;
        return nodeTimeseries = new ClusterNodesTimeseries(scalingDuration(), cluster, nodes, metricsDb);
    }

    public ClusterTimeseries clusterTimeseries() {
        if (clusterTimeseries != null) return clusterTimeseries;
        return clusterTimeseries = metricsDb.getClusterTimeseries(application.id(), cluster.id());
    }

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

    public Load averageLoad() { return nodeTimeseries().averageLoad(); }

    public Load idealLoad() {
        return new Load(idealCpuLoad(), idealMemoryLoad, idealDiskLoad);
    }

    /** Ideal cpu load must take the application traffic fraction into account */
    private double idealCpuLoad() {
        double queryCpuFraction = queryCpuFraction();

        // What's needed to have headroom for growth during scale-up as a fraction of current resources?
        double growthRateHeadroom = 1 + maxQueryGrowthRate() * scalingDuration().toMinutes();
        // Cap headroom at 10% above the historical observed peak
        if (queryFractionOfMax() != 0)
            growthRateHeadroom = Math.min(growthRateHeadroom, 1 / queryFractionOfMax() + 0.1);

        // How much headroom is needed to handle sudden arrival of additional traffic due to another zone going down?
        double maxTrafficShiftHeadroom = 10.0; // Cap to avoid extreme sizes from a current very small share
        double trafficShiftHeadroom;
        if (application.status().maxReadShare() == 0) // No traffic fraction data
            trafficShiftHeadroom = 2.0; // assume we currently get half of the global share of traffic
        else if (application.status().currentReadShare() == 0)
            trafficShiftHeadroom = maxTrafficShiftHeadroom;
        else
            trafficShiftHeadroom = application.status().maxReadShare() / application.status().currentReadShare();
        trafficShiftHeadroom = Math.min(trafficShiftHeadroom, maxTrafficShiftHeadroom);

        // Assumptions: 1) Write load is not organic so we should not grow to handle more.
        //                 (TODO: But allow applications to set their target write rate and size for that)
        //              2) Write load does not change in BCP scenarios.
        return queryCpuFraction * 1 / growthRateHeadroom * 1 / trafficShiftHeadroom * idealQueryCpuLoad +
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

}
