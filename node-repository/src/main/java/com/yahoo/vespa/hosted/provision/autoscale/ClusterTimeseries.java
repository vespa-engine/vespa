// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A list of metric snapshots from a cluster, sorted by increasing time (newest last).
 *
 * @author bratseth
 */
public class ClusterTimeseries {

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
     * @return the predicted max growth of the query rate, per minute as a fraction of the current load
     */
    public double maxQueryGrowthRate(Duration window, Clock clock) {
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
            for (int end = start + 1; end < snapshots.size(); end++) {
                if (queryRateAt(end) >= queryRateAt(start) * 1.3) {
                    Duration duration = durationBetween(start, end);
                    if (duration.toSeconds() == 0) continue;
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
        OptionalDouble queryRate = queryRate(window, clock);
        if (queryRate.orElse(0) == 0) return 0.1; // Growth not expressible as a fraction of the current rate
        return maxGrowthRate * 60 / queryRate.getAsDouble();
    }

    /**
     * The current query rate, averaged over the same window we average utilization over,
     * as a fraction of the peak rate in this timeseries
     */
    public double queryFractionOfMax(Duration window, Clock clock) {
        if (snapshots.isEmpty()) return 0.5;
        var max = snapshots.stream().mapToDouble(ClusterMetricSnapshot::queryRate).max().getAsDouble();
        if (max == 0) return 1.0;
        var average = queryRate(window, clock);
        if (average.isEmpty()) return 0.5; // No measurements in the relevant time period
        return average.getAsDouble() / max;
    }

    /** Returns the average query rate in the given window, or empty if there are no measurements in it */
    public OptionalDouble queryRate(Duration window, Clock clock) {
        Instant oldest = clock.instant().minus(window);
        return snapshots.stream()
                        .filter(snapshot -> ! snapshot.at().isBefore(oldest))
                        .mapToDouble(snapshot -> snapshot.queryRate())
                        .average();
    }

    /** Returns the average query rate in the given window, or empty if there are no measurements in it */
    public OptionalDouble writeRate(Duration window, Clock clock) {
        Instant oldest = clock.instant().minus(window);
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
