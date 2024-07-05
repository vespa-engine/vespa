// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

/**
 * A list of metric snapshots from a cluster, sorted by increasing time (newest last).
 *
 * @author bratseth
 */
public class ClusterTimeseries {

    // The minimum increase in query rate that is considered significant growth.
    private static final double SIGNIFICANT_GROWTH_FACTOR = 0.3;

    // The minimum interval to consider when determining growth rate between snapshots.
    private static final Duration GROWTH_RATE_MIN_INTERVAL = Duration.ofMinutes(5);

    private final ClusterSpec.Id cluster;
    private final List<ClusterMetricSnapshot> snapshots;

    ClusterTimeseries(ClusterSpec.Id cluster, List<ClusterMetricSnapshot> snapshots) {
        this.cluster = cluster;
        List<ClusterMetricSnapshot> sortedSnapshots = new ArrayList<>(snapshots);
        Collections.sort(sortedSnapshots);
        this.snapshots = Collections.unmodifiableList(sortedSnapshots);
    }

    public boolean isEmpty() { return snapshots.isEmpty(); }

    public int size() { return snapshots.size(); }

    public ClusterMetricSnapshot get(int index) { return snapshots.get(index); }

    public List<ClusterMetricSnapshot> asList() { return snapshots; }

    public ClusterSpec.Id cluster() { return cluster; }

    public ClusterTimeseries add(ClusterMetricSnapshot snapshot) {
        List<ClusterMetricSnapshot> list = new ArrayList<>(snapshots);
        list.add(snapshot);
        return new ClusterTimeseries(cluster, list);
    }

    /**
     * The max query growth rate we can predict from this time-series as a fraction of the average traffic in the window
     *
     * This considers query over all known snapshots, but snapshots are effectively bounded in time by the retention
     * period of the metrics database.
     *
     * @return the predicted max growth of the query rate, per minute as a fraction of the current load
     */
    public double maxQueryGrowthRate(Duration window, Instant now) {
        if (snapshots.isEmpty()) return 0.1;
        // Find the period having the highest growth rate, where total growth exceeds 30% increase
        double maxGrowthRate = 0; // In query rate growth per second (to get good resolution)

        for (int start = 0; start < snapshots.size(); start++) {
            if (start > 0) { // Optimization: Skip this point when starting from the previous is better relative to the best rate so far
                Duration duration = durationBetween(start - 1, start);
                if (duration.toSeconds() != 0) {
                    double growthRate = (queryRateAt(start - 1) - queryRateAt(start)) / duration.toSeconds();
                    if (growthRate >= maxGrowthRate)
                        continue;
                }
            }
            // Find a subsequent snapshot where the query rate has increased significantly
            for (int end = start + 1; end < snapshots.size(); end++) {
                Duration duration = durationBetween(start, end);
                if (duration.toSeconds() == 0) continue;
                if (duration.compareTo(GROWTH_RATE_MIN_INTERVAL) < 0) continue; // Too short period to be considered
                if (significantGrowthBetween(start, end)) {
                    double growthRate = (queryRateAt(end) - queryRateAt(start)) / duration.toSeconds();
                    if (growthRate > maxGrowthRate)
                        maxGrowthRate = growthRate;
                }
            }
        }
        if (maxGrowthRate == 0) { // No periods of significant growth
            if (durationBetween(0, snapshots.size() - 1).toHours() < 24)
                return 0.1; //       ... because not much data
            else
                return 0.0; //       ... because load is stable
        }
        OptionalDouble queryRate = queryRate(window, now);
        if (queryRate.orElse(0) == 0) return 0.1; // Growth not expressible as a fraction of the current rate
        return maxGrowthRate * 60 / queryRate.getAsDouble();
    }

    private boolean significantGrowthBetween(int start, int end) {
        return queryRateAt(end) >= queryRateAt(start) * (1 + SIGNIFICANT_GROWTH_FACTOR);
    }

    /**
     * The current query rate, averaged over the same window we average utilization over,
     * as a fraction of the peak rate in this timeseries
     */
    public double queryFractionOfMax(Duration window, Instant now) {
        if (snapshots.isEmpty()) return 0.5;
        var max = snapshots.stream().mapToDouble(ClusterMetricSnapshot::queryRate).max().getAsDouble();
        if (max == 0) return 1.0;
        var average = queryRate(window, now);
        if (average.isEmpty()) return 0.5; // No measurements in the relevant time period
        return average.getAsDouble() / max;
    }

    /** Returns the average query rate in the given window, or empty if there are no measurements in it */
    public OptionalDouble queryRate(Duration window, Instant now) {
        Instant oldest = now.minus(window);
        return snapshots.stream()
                        .filter(snapshot -> ! snapshot.at().isBefore(oldest))
                        .mapToDouble(snapshot -> snapshot.queryRate())
                        .average();
    }

    /** Returns the average query rate in the given window, or empty if there are no measurements in it */
    public OptionalDouble writeRate(Duration window, Instant now) {
        Instant oldest = now.minus(window);
        return snapshots.stream()
                        .filter(snapshot -> ! snapshot.at().isBefore(oldest))
                        .mapToDouble(snapshot -> snapshot.writeRate())
                        .average();
    }

    private double queryRateAt(int index) {
        if (snapshots.isEmpty()) return 0.0;
        return snapshots.get(index).queryRate();
    }

    private double writeRateAt(int index) {
        if (snapshots.isEmpty()) return 0.0;
        return snapshots.get(index).writeRate();
    }

    private Duration durationBetween(int startIndex, int endIndex) {
        return Duration.between(snapshots.get(startIndex).at(), snapshots.get(endIndex).at());
    }

}
