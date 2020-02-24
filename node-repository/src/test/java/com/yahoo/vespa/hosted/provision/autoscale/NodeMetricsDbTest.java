// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NodeMetricsDbTest {

    @Test
    public void testNodeMetricsDb() {
        ManualClock clock = new ManualClock();
        NodeMetricsDb db = new NodeMetricsDb();
        List<NodeMetrics.MetricValue> values = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            values.add(new NodeMetrics.MetricValue("host0", "cpu.util", clock.instant().toEpochMilli(), 0.9f));
            clock.advance(Duration.ofHours(1));
        }
        db.add(values);

        assertEquals(32, db.getWindow(clock.instant().minus(Duration.ofHours(30)), Resource.cpu,    List.of("host0")).measurementCount());
        assertEquals( 0, db.getWindow(clock.instant().minus(Duration.ofHours(30)), Resource.memory, List.of("host0")).measurementCount());
        db.gc(clock);
        assertEquals(26, db.getWindow(clock.instant().minus(Duration.ofHours(30)), Resource.cpu,    List.of("host0")).measurementCount());
        assertEquals( 0, db.getWindow(clock.instant().minus(Duration.ofHours(30)), Resource.memory, List.of("host0")).measurementCount());
    }

}
