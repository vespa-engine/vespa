// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author hakonhall
 * @since 5.34
 */
@RunWith(MockitoJUnitRunner.class)
public class ClusterStatsAggregatorTest {

    final Set<Integer> distributors = new HashSet<>();
    final Set<Integer> storageNodes = new HashSet<>();
    final Map<Integer, String> hostnames = new HashMap<>();
    final MetricUpdater updater = mock(MetricUpdater.class);
    ContentClusterStats clusterStats;

    private void addDistributors(Integer... indices) {
        for (Integer i : indices) {
            distributors.add(i);
        }
    }

    private static class StorageNodeSpec {
        public StorageNodeSpec(Integer index, String hostname) {
            this.index = index;
            this.hostname = hostname;
        }
        public Integer index;
        public String hostname;
    }

    private void addStorageNodes(StorageNodeSpec... specs) {
        for (StorageNodeSpec spec : specs) {
            storageNodes.add(spec.index);
            hostnames.put(spec.index, spec.hostname);
        }
        clusterStats = new ContentClusterStats(storageNodes);
    }

    private void putStorageStats(int index, int syncing, int copyingIn, int movingOut, int copyingOut) {
        clusterStats.getStorageNode(index).set(createStats(index, syncing, copyingIn, movingOut, copyingOut));
    }

    private static NodeMergeStats createStats(int index, int syncing, int copyingIn, int movingOut, int copyingOut) {
        return new NodeMergeStats(
                index,
                new NodeMergeStats.Amount(syncing),
                new NodeMergeStats.Amount(copyingIn),
                new NodeMergeStats.Amount(movingOut),
                new NodeMergeStats.Amount(copyingOut));
    }

    @Test
    public void testSimple() {
        final int distributorIndex = 1;
        addDistributors(distributorIndex);

        final int storageNodeIndex = 11;
        addStorageNodes(new StorageNodeSpec(storageNodeIndex, "storage-node"));

        putStorageStats(storageNodeIndex, 5, 6, 7, 8);

        ClusterStatsAggregator aggregator = new ClusterStatsAggregator(distributors, storageNodes, updater);
        aggregator.updateForDistributor(hostnames, distributorIndex, clusterStats);

        Map<String, NodeMergeStats> expectedStorageNodeStats = new HashMap<>();
        expectedStorageNodeStats.put("storage-node", createStats(storageNodeIndex, 5, 6, 7, 8));

        verify(updater).updateMergeOpMetrics(expectedStorageNodeStats);
    }

    @Test
    public void testComplex() {
        final int distributor1 = 1;
        final int distributor2 = 2;
        addDistributors(distributor1, distributor2);

        final int storageNode1 = 11;
        final int storageNode2 = 12;
        addStorageNodes(
                new StorageNodeSpec(storageNode1, "storage-node-1"),
                new StorageNodeSpec(storageNode2, "storage-node-2"));

        ClusterStatsAggregator aggregator = new ClusterStatsAggregator(distributors, storageNodes, updater);

        // Distributor 1.
        putStorageStats(storageNode1, 0, 1, 2, 3);
        putStorageStats(storageNode2, 20, 21, 22, 23);
        aggregator.updateForDistributor(hostnames, distributor1, clusterStats);

        // Distributor 2.
        putStorageStats(storageNode1, 10, 11, 12, 13);
        putStorageStats(storageNode2, 30, 31, 32, 33);
        aggregator.updateForDistributor(hostnames, distributor2, clusterStats);

        Map<String, NodeMergeStats> expectedStorageNodeStats = new HashMap<>();
        expectedStorageNodeStats.put("storage-node-1", createStats(storageNode1, 0 + 10, 1 + 11, 2 + 12, 3 + 13));
        expectedStorageNodeStats.put("storage-node-2", createStats(storageNode2, 20 + 30, 21 + 31, 22 + 32, 23 + 33));

        verify(updater, times(1)).updateMergeOpMetrics(expectedStorageNodeStats);
    }

    @Test
    public void testHashCodeCache() {
        final int distributor1 = 1;
        final int distributor2 = 2;
        addDistributors(distributor1, distributor2);

        final int storageNode1 = 11;
        final int storageNode2 = 12;
        addStorageNodes(
                new StorageNodeSpec(storageNode1, "storage-node-1"),
                new StorageNodeSpec(storageNode2, "storage-node-2"));

        ClusterStatsAggregator aggregator = new ClusterStatsAggregator(distributors, storageNodes, updater);

        // Distributor 1.
        putStorageStats(storageNode1, 0, 1, 2, 3);
        putStorageStats(storageNode2, 20, 21, 22, 23);
        aggregator.updateForDistributor(hostnames, distributor1, clusterStats);

        // Distributor 2.
        putStorageStats(storageNode1, 10, 11, 12, 13);
        putStorageStats(storageNode2, 30, 31, 32, 33);
        aggregator.updateForDistributor(hostnames, distributor2, clusterStats);

        // If we add call another updateForDistributor with the same arguments, updateMergeOpMetrics() should not be called.
        // See times(1) below.
        aggregator.updateForDistributor(hostnames, distributor2, clusterStats);

        Map<String, NodeMergeStats> expectedStorageNodeStats = new HashMap<>();
        expectedStorageNodeStats.put("storage-node-1", createStats(storageNode1, 0 + 10, 1 + 11, 2 + 12, 3 + 13));
        expectedStorageNodeStats.put("storage-node-2", createStats(storageNode2, 20 + 30, 21 + 31, 22 + 32, 23 + 33));


        verify(updater, times(1)).updateMergeOpMetrics(expectedStorageNodeStats);
    }

    @Test
    public void testUnknownDistributor() {
        final int upDistributor = 1;
        final int DownDistributorIndex = 2;
        addDistributors(upDistributor);

        final int storageNodeIndex = 11;
        addStorageNodes(new StorageNodeSpec(storageNodeIndex, "storage-node"));

        putStorageStats(storageNodeIndex, 5, 6, 7, 8);

        ClusterStatsAggregator aggregator = new ClusterStatsAggregator(distributors, storageNodes, updater);
        aggregator.updateForDistributor(hostnames, DownDistributorIndex, clusterStats);

        verify(updater, never()).updateMergeOpMetrics(any());
    }

    @Test
    public void testMoreStorageNodesThanDistributors() {
        final int distributor1 = 1;
        addDistributors(distributor1);

        final int storageNode1 = 11;
        final int storageNode2 = 12;
        addStorageNodes(
                new StorageNodeSpec(storageNode1, "storage-node-1"),
                new StorageNodeSpec(storageNode2, "storage-node-2"));

        ClusterStatsAggregator aggregator = new ClusterStatsAggregator(distributors, storageNodes, updater);

        // Distributor 1.
        putStorageStats(storageNode1, 0, 1, 2, 3);
        putStorageStats(storageNode2, 20, 21, 22, 23);
        aggregator.updateForDistributor(hostnames, distributor1, clusterStats);

        Map<String, NodeMergeStats> expectedStorageNodeStats = new HashMap<>();
        expectedStorageNodeStats.put("storage-node-1", createStats(storageNode1, 0, 1, 2, 3));
        expectedStorageNodeStats.put("storage-node-2", createStats(storageNode2, 20, 21, 22, 23));

        verify(updater, times(1)).updateMergeOpMetrics(expectedStorageNodeStats);
    }

    @Test
    public void testMoreDistributorsThanStorageNodes() {
        final int distributor1 = 1;
        final int distributor2 = 2;
        addDistributors(distributor1, distributor2);

        final int storageNode1 = 11;
        addStorageNodes(new StorageNodeSpec(storageNode1, "storage-node-1"));

        ClusterStatsAggregator aggregator = new ClusterStatsAggregator(distributors, storageNodes, updater);

        // Distributor 1.
        putStorageStats(storageNode1, 0, 1, 2, 3);
        aggregator.updateForDistributor(hostnames, distributor1, clusterStats);

        // Distributor 2.
        putStorageStats(storageNode1, 10, 11, 12, 13);
        aggregator.updateForDistributor(hostnames, distributor2, clusterStats);

        Map<String, NodeMergeStats> expectedStorageNodeStats = new HashMap<>();
        expectedStorageNodeStats.put("storage-node-1", createStats(storageNode1, 0 + 10, 1 + 11, 2 + 12, 3 + 13));

        verify(updater, times(1)).updateMergeOpMetrics(expectedStorageNodeStats);
    }
}
