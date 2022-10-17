// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.io.IOUtils;
import com.yahoo.test.ManualClock;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the Quest metrics db.
 *
 * @author bratseth
 */
public class QuestMetricsDbTest {

    private static final double delta = 0.0000001;

    @Test
    public void testNodeMetricsReadWrite() {
        String dataDir = createEmptyDataDir("QuestMetricsDbReadWrite", "metrics");
        ManualClock clock = new ManualClock("2020-10-01T00:00:00");
        QuestMetricsDb db = new QuestMetricsDb(dataDir, clock);
        Instant startTime = clock.instant();

        clock.advance(Duration.ofSeconds(1));
        db.addNodeMetrics(nodeTimeseries(1000, Duration.ofSeconds(1), clock, "host1", "host2", "host3"));

        clock.advance(Duration.ofSeconds(1));

        // Read all of one host
        List<NodeTimeseries> nodeTimeSeries1 = db.getNodeTimeseries(Duration.between(startTime, clock.instant()),
                                                                    Set.of("host1"));
        assertEquals(1, nodeTimeSeries1.size());
        assertEquals("host1", nodeTimeSeries1.get(0).hostname());
        assertEquals(1000, nodeTimeSeries1.get(0).size());
        NodeMetricSnapshot snapshot = nodeTimeSeries1.get(0).asList().get(0);
        assertEquals(startTime.plus(Duration.ofSeconds(1)), snapshot.at());
        assertEquals(0.1, snapshot.load().cpu(), delta);
        assertEquals(0.2, snapshot.load().memory(), delta);
        assertEquals(0.4, snapshot.load().disk(), delta);
        assertEquals(1, snapshot.generation(), delta);
        assertEquals(30, snapshot.queryRate(), delta);

        // Read all from 2 hosts
        List<NodeTimeseries> nodeTimeSeries2 = db.getNodeTimeseries(Duration.between(startTime, clock.instant()),
                                                                    Set.of("host2", "host3"));
        assertEquals(2, nodeTimeSeries2.size());
        assertEquals(Set.of("host2", "host3"), nodeTimeSeries2.stream().map(ts -> ts.hostname()).collect(Collectors.toSet()));
        assertEquals(1000, nodeTimeSeries2.get(0).size());
        assertEquals(1000, nodeTimeSeries2.get(1).size());

        // Read a short interval from 3 hosts
        List<NodeTimeseries> nodeTimeSeries3 = db.getNodeTimeseries(Duration.ofSeconds(3),
                                                                    Set.of("host1", "host2", "host3"));
        assertEquals(3, nodeTimeSeries3.size());
        assertEquals(Set.of("host1", "host2", "host3"), nodeTimeSeries3.stream().map(ts -> ts.hostname()).collect(Collectors.toSet()));
        assertEquals(2, nodeTimeSeries3.get(0).size());
        assertEquals(2, nodeTimeSeries3.get(1).size());
        assertEquals(2, nodeTimeSeries3.get(2).size());
    }

    @Test
    public void testClusterMetricsReadWrite() {
        String dataDir = createEmptyDataDir("QuestMetricsDbReadWrite", "clusterMetrics");
        ManualClock clock = new ManualClock("2020-10-01T00:00:00");
        QuestMetricsDb db = new QuestMetricsDb(dataDir, clock);
        Instant startTime = clock.instant();

        var application1 = ApplicationId.from("t1", "a1", "i1");
        var application2 = ApplicationId.from("t1", "a2", "i1");
        var cluster1 = new ClusterSpec.Id("cluster1");
        var cluster2 = new ClusterSpec.Id("cluster2");
        db.addClusterMetrics(application1, Map.of(cluster1, new ClusterMetricSnapshot(clock.instant(), 30.0, 15.0)));
        db.addClusterMetrics(application1, Map.of(cluster2, new ClusterMetricSnapshot(clock.instant(), 60.0, 30.0)));
        clock.advance(Duration.ofMinutes(1));
        db.addClusterMetrics(application1, Map.of(cluster1, new ClusterMetricSnapshot(clock.instant(), 45.0, 22.5)));
        clock.advance(Duration.ofMinutes(1));
        db.addClusterMetrics(application2, Map.of(cluster1, new ClusterMetricSnapshot(clock.instant(), 90.0, 45.0)));

        ClusterTimeseries clusterTimeseries11 = db.getClusterTimeseries(application1, cluster1);
        assertEquals(cluster1, clusterTimeseries11.cluster());
        assertEquals(2, clusterTimeseries11.asList().size());

        ClusterMetricSnapshot snapshot111 = clusterTimeseries11.get(0);
        assertEquals(startTime, snapshot111.at());
        assertEquals(30, snapshot111.queryRate(), delta);
        assertEquals(15, snapshot111.writeRate(), delta);
        ClusterMetricSnapshot snapshot112 = clusterTimeseries11.get(1);
        assertEquals(startTime.plus(Duration.ofMinutes(1)), snapshot112.at());
        assertEquals(45, snapshot112.queryRate(), delta);
        assertEquals(22.5, snapshot112.writeRate(), delta);


        ClusterTimeseries clusterTimeseries12 = db.getClusterTimeseries(application1, cluster2);
        assertEquals(cluster2, clusterTimeseries12.cluster());
        assertEquals(1, clusterTimeseries12.asList().size());

        ClusterMetricSnapshot snapshot121 = clusterTimeseries12.get(0);
        assertEquals(startTime, snapshot121.at());
        assertEquals(60, snapshot121.queryRate(), delta);
        assertEquals(30, snapshot121.writeRate(), delta);


        ClusterTimeseries clusterTimeseries21 = db.getClusterTimeseries(application2, cluster1);
        assertEquals(cluster1, clusterTimeseries21.cluster());
        assertEquals(1, clusterTimeseries21.asList().size());

        ClusterMetricSnapshot snapshot211 = clusterTimeseries21.get(0);
        assertEquals(startTime.plus(Duration.ofMinutes(2)), snapshot211.at());
        assertEquals(90, snapshot211.queryRate(), delta);
    }

    @Test
    public void testWriteOldData() {
        String dataDir = createEmptyDataDir("QuestMetricsDbWriteOldData", "metrics");
        ManualClock clock = new ManualClock("2020-10-01T00:00:00");
        QuestMetricsDb db = new QuestMetricsDb(dataDir, clock);
        Instant startTime = clock.instant();
        clock.advance(Duration.ofSeconds(300));
        db.addNodeMetrics(timeseriesAt(10, clock.instant(), "host1", "host2", "host3"));
        clock.advance(Duration.ofSeconds(1));

        List<NodeTimeseries> nodeTimeSeries1 = db.getNodeTimeseries(Duration.between(startTime, clock.instant()),
                                                                    Set.of("host1"));
        assertEquals(10, nodeTimeSeries1.get(0).size());

        db.addNodeMetrics(timeseriesAt(10, clock.instant().minus(Duration.ofSeconds(20)), "host1", "host2", "host3"));
        List<NodeTimeseries> nodeTimeSeries2 = db.getNodeTimeseries(Duration.between(startTime, clock.instant()),
                                                                    Set.of("host1"));
        assertEquals("Recent data is accepted", 20, nodeTimeSeries2.get(0).size());

        db.addNodeMetrics(timeseriesAt(10, clock.instant().minus(Duration.ofSeconds(200)), "host1", "host2", "host3"));
        List<NodeTimeseries> nodeTimeSeries3 = db.getNodeTimeseries(Duration.between(startTime, clock.instant()),
                                                                    Set.of("host1"));
        assertEquals("Too old data is rejected", 20, nodeTimeSeries3.get(0).size());
    }

    @Test
    public void testGc() {
        String dataDir = createEmptyDataDir("QuestMetricsDbGc", "metrics");
        ManualClock clock = new ManualClock();
        int days = 10; // The first metrics are this many days in the past
        clock.retreat(Duration.ofDays(10));
        Instant startTime = clock.instant();

        QuestMetricsDb db = new QuestMetricsDb(dataDir, clock);

        db.addNodeMetrics(nodeTimeseries(24 * days, Duration.ofHours(1), clock, "host1", "host2", "host3"));

        var application1 = ApplicationId.from("t1", "a1", "i1");
        var cluster1 = new ClusterSpec.Id("cluster1");
        db.addClusterMetrics(application1, Map.of(cluster1, new ClusterMetricSnapshot(clock.instant(), 30.0, 15.0)));

        assertEquals(24 * days, db.getNodeTimeseries(Duration.between(startTime, clock.instant()),
                                                              Set.of("host1")).get(0).size());
        db.gc();
        assertTrue(db.getNodeTimeseries(Duration.between(startTime, clock.instant()), Set.of("host1")).get(0).size() < 24 * 4);
        db.gc(); // no-op
        assertTrue(db.getNodeTimeseries(Duration.between(startTime, clock.instant()), Set.of("host1")).get(0).size() < 24 * 4);
    }

    /** To manually test that we can read existing data */
    @Ignore
    @Test
    public void testReadingAndAppendingToExistingData() {
        String dataDir = dataDir("QuestMetricsDbExistingData");
        if ( ! new File(dataDir).exists()) {
            System.out.println("No existing data to check");
            return;
        }
        IOUtils.createDirectory(dataDir + "/metrics");
        ManualClock clock = new ManualClock("2020-10-01T00:00:00");
        clock.advance(Duration.ofSeconds(9)); // Adjust to last data written
        QuestMetricsDb db = new QuestMetricsDb(dataDir, clock);

        List<NodeTimeseries> timeseries = db.getNodeTimeseries(Duration.ofSeconds(9), Set.of("host1"));
        assertFalse("Could read existing data", timeseries.isEmpty());
        assertEquals(10, timeseries.get(0).size());

        System.out.println("Existing data read:");
        for (var snapshot : timeseries.get(0).asList())
            System.out.println("  " + snapshot);

        clock.advance(Duration.ofSeconds(1));
        db.addNodeMetrics(nodeTimeseries(2, Duration.ofSeconds(1), clock, "host1"));
        System.out.println("New data written and read:");
        timeseries = db.getNodeTimeseries(Duration.ofSeconds(2), Set.of("host1"));
        for (var snapshot : timeseries.get(0).asList())
            System.out.println("  " + snapshot);
    }

    /** To update data for the manual test above */
    @Ignore
    @Test
    public void updateExistingData() {
        String dataDir = createEmptyDataDir("QuestMetricsDbExistingData", "metrics");
        ManualClock clock = new ManualClock("2020-10-01T00:00:00");
        QuestMetricsDb db = new QuestMetricsDb(dataDir, clock);
        Instant startTime = clock.instant();
        db.addNodeMetrics(nodeTimeseries(10, Duration.ofSeconds(1), clock, "host1"));

        int added = db.getNodeTimeseries(Duration.between(startTime, clock.instant()),
                                         Set.of("host1")).get(0).asList().size();
        System.out.println("Added " + added + " rows of data");
        db.close();
    }

    private Collection<Pair<String, NodeMetricSnapshot>> nodeTimeseries(int countPerHost, Duration sampleRate, ManualClock clock,
                                                                        String ... hosts) {
        Collection<Pair<String, NodeMetricSnapshot>> timeseries = new ArrayList<>();
        for (int i = 1; i <= countPerHost; i++) {
            for (String host : hosts)
                timeseries.add(new Pair<>(host, new NodeMetricSnapshot(clock.instant(),
                                                                       new Load(i * 0.1, i * 0.2, i * 0.4),
                                                                       i % 100,
                                                                       true,
                                                                       true,
                                                                       30.0)));
            clock.advance(sampleRate);
        }
        return timeseries;
    }

    private Collection<Pair<String, NodeMetricSnapshot>> timeseriesAt(int countPerHost, Instant at, String ... hosts) {
        Collection<Pair<String, NodeMetricSnapshot>> timeseries = new ArrayList<>();
        for (int i = 1; i <= countPerHost; i++) {
            for (String host : hosts)
                timeseries.add(new Pair<>(host, new NodeMetricSnapshot(at, new Load(i * 0.1, i * 0.2, i * 0.4),
                                                                       i % 100,
                                                                       true,
                                                                       false,
                                                                       0.0)));
        }
        return timeseries;
    }

    private static String dataDir(String name) {
        return "target/questdb/" + name;
    }

    private static String createEmptyDataDir(String name, String... subPath) {
        String dataDir = dataDir(name);
        IOUtils.recursiveDeleteDir(new File(dataDir));
        String path = Stream.concat(Stream.of(dataDir), Arrays.stream(subPath))
                            .collect(Collectors.joining("/"));
        IOUtils.createDirectory(path);
        return dataDir;
    }

}
