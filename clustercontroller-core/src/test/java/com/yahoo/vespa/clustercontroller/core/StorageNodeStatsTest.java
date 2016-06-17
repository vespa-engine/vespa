// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author hakonhall
 */
public class StorageNodeStatsTest {
    @Test
    public void testStorageNodeStats() {
        LatencyStats putLatency = new LatencyStats(1, 2);
        StorageNodeStats stats = new StorageNodeStats(putLatency);
        assertEquals(1, stats.getDistributorPutLatency().getLatencyMsSum());
        assertEquals(2, stats.getDistributorPutLatency().getCount());

        LatencyStats putLatencyToAdd = new LatencyStats(3, 4);
        StorageNodeStats statsToAdd = new StorageNodeStats(putLatencyToAdd);
        stats.add(statsToAdd);
        assertEquals(1 + 3, stats.getDistributorPutLatency().getLatencyMsSum());
        assertEquals(2 + 4, stats.getDistributorPutLatency().getCount());
    }
}
