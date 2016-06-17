// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author hakonhall
 */
public class StorageNodeStatsContainerTest {
    @Test
    public void testStatsForStorage() {
        StorageNodeStatsContainer statsContainer = new StorageNodeStatsContainer();
        Map<Integer, StorageNodeStats> statsMap = new HashMap<>();

        LatencyStats putLatencyForA = new LatencyStats(1, 2);
        StorageNodeStats nodeStatsForA = new StorageNodeStats(putLatencyForA);
        statsContainer.put(5, nodeStatsForA);

        LatencyStats putLatencyForB = new LatencyStats(3, 4);
        StorageNodeStats nodeStatsForB = new StorageNodeStats(putLatencyForB);
        statsContainer.put(6, nodeStatsForB);

        StorageNodeStats nodeStats = statsContainer.get(5);
        assertNotNull(nodeStats);
        assertEquals(1, nodeStatsForA.getDistributorPutLatency().getLatencyMsSum());
        assertEquals(2, nodeStatsForA.getDistributorPutLatency().getCount());

        nodeStats = statsContainer.get(6);
        assertNotNull(nodeStats);
        assertEquals(3, nodeStatsForB.getDistributorPutLatency().getLatencyMsSum());
        assertEquals(4, nodeStatsForB.getDistributorPutLatency().getCount());

        nodeStats = statsContainer.get(7);
        assertNull(nodeStats);
    }
}
