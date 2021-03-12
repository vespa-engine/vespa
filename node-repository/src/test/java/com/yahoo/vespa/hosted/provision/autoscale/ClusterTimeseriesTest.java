// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ClusterTimeseriesTest {

    private static final double delta = 0.001;
    private static final ClusterSpec.Id cluster = new ClusterSpec.Id("test");

    @Test
    public void test_empty() {
        var timeseries = new ClusterTimeseries(cluster, List.of());
        assertEquals(0.1, timeseries.maxQueryGrowthRate(), delta);
    }

    @Test
    public void test_constant_rate_short() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10, clock, t -> 50.0));
        assertEquals(0.1, timeseries.maxQueryGrowthRate(), delta);
    }

    @Test
    public void test_constant_rate_long() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10000, clock, t -> 50.0));
        assertEquals(0.0, timeseries.maxQueryGrowthRate(), delta);
    }

    @Test
    public void test_single_spike() {
        var clock = new ManualClock();
        var snapshots = new ArrayList<ClusterMetricSnapshot>();
        snapshots.addAll(queryRate(1000, clock, t ->  50.0));
        snapshots.addAll(queryRate(10, clock, t -> 400.0));
        snapshots.addAll(queryRate(1000, clock, t ->  50.0));
        assertEquals((400-50)/5.0/50.0, new ClusterTimeseries(cluster, snapshots).maxQueryGrowthRate(), delta);
    }

    @Test
    public void test_three_spikes() {
        var clock = new ManualClock();
        var snapshots = new ArrayList<ClusterMetricSnapshot>();
        snapshots.addAll(queryRate(1000, clock, t ->  50.0));
        snapshots.addAll(queryRate(10, clock, t -> 400.0));
        snapshots.addAll(queryRate(1000, clock, t ->  50.0));
        snapshots.addAll(queryRate(10, clock, t -> 600.0));
        snapshots.addAll(queryRate(1000, clock, t ->  50.0));
        snapshots.addAll(queryRate(10, clock, t -> 800.0));
        snapshots.addAll(queryRate(1000, clock, t ->  50.0));
        assertEquals((800-50)/5.0/50.0, new ClusterTimeseries(cluster, snapshots).maxQueryGrowthRate(), delta);
    }

    @Test
    public void test_single_hill() {
        var clock = new ManualClock();
        var snapshots = new ArrayList<ClusterMetricSnapshot>();
        snapshots.addAll(queryRate(100, clock, t ->  (double)t));
        snapshots.addAll(queryRate(100, clock, t -> 100.0 - t));
        assertEquals(1/5.0, new ClusterTimeseries(cluster, snapshots).maxQueryGrowthRate(), delta);
    }

    @Test
    public void test_smooth_curve() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10000, clock,
                                                             t -> 10.0 + 100.0 * Math.sin(t)));
        assertEquals(0.26, timeseries.maxQueryGrowthRate(), delta);
    }

    @Test
    public void test_smooth_curve_small_variation() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10000, clock,
                                                                           t -> 1000.0 + 10.0 * Math.sin(t)));
        assertEquals(0.0, timeseries.maxQueryGrowthRate(), delta);
    }

    @Test
    public void test_two_periods() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10000, clock,
                                                             t -> 10.0 + 100.0 * Math.sin(t) + 80.0 * Math.sin(10 * t)) );
        assertEquals(1.765, timeseries.maxQueryGrowthRate(), delta);
    }

    private List<ClusterMetricSnapshot> queryRate(int count, ManualClock clock, IntFunction<Double> rate) {
        List<ClusterMetricSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            snapshots.add(new ClusterMetricSnapshot(clock.instant(), rate.apply(i), 0.0));
            clock.advance(Duration.ofMinutes(5));
        }
        return snapshots;
    }

}
