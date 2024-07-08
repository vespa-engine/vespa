// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.test.ManualClock;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ClusterTimeseriesTest {

    private static final double delta = 0.001;
    private static final ClusterSpec.Id cluster = new ClusterSpec.Id("test");
    private static final String testDataPath = "src/test/java/com/yahoo/vespa/hosted/provision/autoscale/testdata";

    @Test
    public void test_empty() {
        ManualClock clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, List.of());
        assertEquals(0.1, timeseries.maxQueryGrowthRate(Duration.ofMinutes(5), clock.instant()), delta);
    }

    @Test
    public void test_constant_rate_short() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10, clock, t -> 50.0));
        assertEquals(0.1, timeseries.maxQueryGrowthRate(Duration.ofMinutes(5), clock.instant()), delta);
    }

    @Test
    public void test_constant_rate_long() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10000, clock, t -> 50.0));
        assertEquals(0.0, timeseries.maxQueryGrowthRate(Duration.ofMinutes(5), clock.instant()), delta);
    }

    @Test
    public void test_single_spike() {
        var clock = new ManualClock();
        var snapshots = new ArrayList<ClusterMetricSnapshot>();
        snapshots.addAll(queryRate(1000, clock, t ->  50.0));
        snapshots.addAll(queryRate(10, clock, t -> 400.0));
        snapshots.addAll(queryRate(1000, clock, t ->  50.0));
        assertEquals((400-50)/5.0/50.0, new ClusterTimeseries(cluster, snapshots).maxQueryGrowthRate(Duration.ofMinutes(5), clock.instant()), delta);
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
        assertEquals((800-50)/5.0/50.0, new ClusterTimeseries(cluster, snapshots).maxQueryGrowthRate(Duration.ofMinutes(5), clock.instant()), delta);
    }

    @Test
    public void test_single_hill() {
        var clock = new ManualClock();
        var snapshots = new ArrayList<ClusterMetricSnapshot>();
        snapshots.addAll(queryRate(100, clock, t ->  (double)t));
        snapshots.addAll(queryRate(100, clock, t -> 100.0 - t));
        assertEquals(1/5.0, new ClusterTimeseries(cluster, snapshots).maxQueryGrowthRate(Duration.ofMinutes(5), clock.instant()), delta);
    }

    @Test
    public void test_smooth_curve() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10000, clock,
                                                             t -> 10.0 + 100.0 * Math.sin(t)));
        assertEquals(0.26, timeseries.maxQueryGrowthRate(Duration.ofMinutes(5), clock.instant()), delta);
    }

    @Test
    public void test_smooth_curve_small_variation() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10000, clock,
                                                                           t -> 1000.0 + 10.0 * Math.sin(t)));
        assertEquals(0.0, timeseries.maxQueryGrowthRate(Duration.ofMinutes(5), clock.instant()), delta);
    }

    @Test
    public void test_two_periods() {
        var clock = new ManualClock();
        var timeseries = new ClusterTimeseries(cluster, queryRate(10000, clock,
                                                             t -> 10.0 + 100.0 * Math.sin(t) + 80.0 * Math.sin(10 * t)) );
        assertEquals(1.765, timeseries.maxQueryGrowthRate(Duration.ofMinutes(5), clock.instant()), delta);
    }

    @Test
    public void test_real_data() {
        // Here we use real data from a production deployment, where significant query rate growth is measured over
        // a short period (ts0). This should not cause a significant difference in max growth rate compared to a node
        // measuring approximately the same growth
        Instant scaleAt = Instant.parse("2024-07-03T06:06:57Z");
        ClusterTimeseries ts0 = new ClusterTimeseries(cluster, readSnapshots("real-traffic-fast-growth"));
        double maxGrowthRate0 = ts0.maxQueryGrowthRate(Duration.ofMinutes(5), scaleAt);
        assertEquals(0.0896, maxGrowthRate0, delta);
        ClusterTimeseries ts1 = new ClusterTimeseries(cluster, readSnapshots("real-traffic-ordinary-growth"));
        double maxGrowthRate1 = ts1.maxQueryGrowthRate(Duration.ofMinutes(5), scaleAt);
        assertEquals(0.0733, maxGrowthRate1, delta);
        assertTrue(maxGrowthRate0 - maxGrowthRate1 < 0.1);
    }

    private List<ClusterMetricSnapshot> readSnapshots(String filename) {
        return Exceptions.uncheck(() -> Files.readAllLines(Paths.get(testDataPath, filename))).stream()
                         .map(this::parseSnapshot)
                         .toList();
    }

    private ClusterMetricSnapshot parseSnapshot(String text) {
        String[] parts = text.split(" ");
        Instant instant = Instant.parse(parts[0].replace("at=", ""));
        double rate = Double.parseDouble(parts[1].replace("queryRate=", ""));
        return new ClusterMetricSnapshot(instant, rate, 0);
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
