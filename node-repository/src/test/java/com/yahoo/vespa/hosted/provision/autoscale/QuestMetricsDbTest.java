// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.io.IOUtils;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Tests the Quest metrics db.
 *
 * @author bratseth
 */
public class QuestMetricsDbTest {

    private static final double delta = 0.0000001;

    @Test
    public void testReadWrite() {
        String dataDir = "data/QuestMetricsDbReadWrite";
        IOUtils.recursiveDeleteDir(new File(dataDir));
        IOUtils.createDirectory(dataDir + "/metrics");
        ManualClock clock = new ManualClock("2020-10-01T00:00:00");
        QuestMetricsDb db = new QuestMetricsDb(dataDir, clock);
        Instant startTime = clock.instant();
        clock.advance(Duration.ofSeconds(1));
        db.add(timeseries(1000, Duration.ofSeconds(1), clock, "host1", "host2", "host3"));

        clock.advance(Duration.ofSeconds(1));

        // Read all of one host
        List<NodeTimeseries> nodeTimeSeries1 = db.getNodeTimeseries(startTime, Set.of("host1"));
        assertEquals(1, nodeTimeSeries1.size());
        assertEquals("host1", nodeTimeSeries1.get(0).hostname());
        assertEquals(1000, nodeTimeSeries1.get(0).size());
        MetricSnapshot snapshot = nodeTimeSeries1.get(0).asList().get(0);
        assertEquals(startTime.plus(Duration.ofSeconds(1)), snapshot.at());
        assertEquals(0.1, snapshot.cpu(), delta);
        assertEquals(0.2, snapshot.memory(), delta);
        assertEquals(0.4, snapshot.disk(), delta);
        assertEquals(1, snapshot.generation(), delta);

        // Read all from 2 hosts
        List<NodeTimeseries> nodeTimeSeries2 = db.getNodeTimeseries(startTime, Set.of("host2", "host3"));
        assertEquals(2, nodeTimeSeries2.size());
        assertEquals(Set.of("host2", "host3"), nodeTimeSeries2.stream().map(ts -> ts.hostname()).collect(Collectors.toSet()));
        assertEquals(1000, nodeTimeSeries2.get(0).size());
        assertEquals(1000, nodeTimeSeries2.get(1).size());

        // Read a short interval from 3 hosts
        List<NodeTimeseries> nodeTimeSeries3 = db.getNodeTimeseries(clock.instant().minus(Duration.ofSeconds(3)),
                                                                    Set.of("host1", "host2", "host3"));
        assertEquals(3, nodeTimeSeries3.size());
        assertEquals(Set.of("host1", "host2", "host3"), nodeTimeSeries3.stream().map(ts -> ts.hostname()).collect(Collectors.toSet()));
        assertEquals(2, nodeTimeSeries3.get(0).size());
        assertEquals(2, nodeTimeSeries3.get(1).size());
        assertEquals(2, nodeTimeSeries3.get(2).size());
    }

    @Test
    public void testWriteOldData() {
        String dataDir = "data/QuestMetricsDbWriteOldData";
        IOUtils.recursiveDeleteDir(new File(dataDir));
        IOUtils.createDirectory(dataDir + "/metrics");
        ManualClock clock = new ManualClock("2020-10-01T00:00:00");
        QuestMetricsDb db = new QuestMetricsDb(dataDir, clock);
        Instant startTime = clock.instant();
        clock.advance(Duration.ofSeconds(300));
        db.add(timeseriesAt(10, clock.instant(), "host1", "host2", "host3"));
        clock.advance(Duration.ofSeconds(1));

        List<NodeTimeseries> nodeTimeSeries1 = db.getNodeTimeseries(startTime, Set.of("host1"));
        assertEquals(10, nodeTimeSeries1.get(0).size());

        db.add(timeseriesAt(10, clock.instant().minus(Duration.ofSeconds(20)), "host1", "host2", "host3"));
        List<NodeTimeseries> nodeTimeSeries2 = db.getNodeTimeseries(startTime, Set.of("host1"));
        assertEquals("Recent data is accepted", 20, nodeTimeSeries2.get(0).size());

        db.add(timeseriesAt(10, clock.instant().minus(Duration.ofSeconds(200)), "host1", "host2", "host3"));
        List<NodeTimeseries> nodeTimeSeries3 = db.getNodeTimeseries(startTime, Set.of("host1"));
        assertEquals("Too old data is rejected", 20, nodeTimeSeries3.get(0).size());
    }

    @Test
    public void testGc() {
        String dataDir = "data/QuestMetricsDbGc";
        IOUtils.recursiveDeleteDir(new File(dataDir));
        IOUtils.createDirectory(dataDir + "/metrics");
        ManualClock clock = new ManualClock("2020-10-01T00:00:00");
        QuestMetricsDb db = new QuestMetricsDb(dataDir, clock);
        Instant startTime = clock.instant();
        int dayOffset = 3;
        clock.advance(Duration.ofHours(dayOffset));
        db.add(timeseries(24 * 10, Duration.ofHours(1), clock, "host1", "host2", "host3"));

        assertEquals(24 * 10, db.getNodeTimeseries(startTime, Set.of("host1")).get(0).size());
        db.gc();
        assertEquals(24 * 1 + dayOffset, db.getNodeTimeseries(startTime, Set.of("host1")).get(0).size());
        db.gc(); // no-op
        assertEquals(24 * 1 + dayOffset, db.getNodeTimeseries(startTime, Set.of("host1")).get(0).size());
    }

    private Collection<Pair<String, MetricSnapshot>> timeseries(int countPerHost, Duration sampleRate, ManualClock clock,
                                                                String ... hosts) {
        Collection<Pair<String, MetricSnapshot>> timeseries = new ArrayList<>();
        for (int i = 1; i <= countPerHost; i++) {
            for (String host : hosts)
                timeseries.add(new Pair<>(host, new MetricSnapshot(clock.instant(),
                                                                   i * 0.1,
                                                                   i * 0.2,
                                                                   i * 0.4,
                                                                   i % 100)));
            clock.advance(sampleRate);
        }
        return timeseries;
    }

    private Collection<Pair<String, MetricSnapshot>> timeseriesAt(int countPerHost, Instant at, String ... hosts) {
        Collection<Pair<String, MetricSnapshot>> timeseries = new ArrayList<>();
        for (int i = 1; i <= countPerHost; i++) {
            for (String host : hosts)
                timeseries.add(new Pair<>(host, new MetricSnapshot(at,
                                                                   i * 0.1,
                                                                   i * 0.2,
                                                                   i * 0.4,
                                                                   i % 100)));
        }
        return timeseries;
    }
}
