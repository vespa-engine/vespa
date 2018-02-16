// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.yahoo.vespa.clustercontroller.core.NodeMergeStats;
import com.yahoo.vespa.clustercontroller.core.ContentClusterStats;
import com.yahoo.vespa.clustercontroller.core.StorageNodeStats;
import com.yahoo.vespa.clustercontroller.core.StorageNodeStatsContainer;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author hakonhall
 */
public class StorageNodeStatsBridgeTest {

    private static String getJsonString() throws IOException {
        Path path = Paths.get("../protocols/getnodestate/host_info.json");
        byte[] encoded;
        encoded = Files.readAllBytes(path);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    @Test
    public void testStorageNodeStatsContainer() throws IOException {
        String data = getJsonString();
        HostInfo hostInfo = HostInfo.createHostInfo(data);
        StorageNodeStatsContainer container = StorageNodeStatsBridge.traverseHostInfo(hostInfo);
        assertEquals(2, container.size());

        StorageNodeStats node0 = container.get(0);
        assertNotNull(node0);
        assertEquals(15, node0.getDistributorPutLatency().getLatencyMsSum());
        assertEquals(16, node0.getDistributorPutLatency().getCount());

        StorageNodeStats node1 = container.get(1);
        assertNotNull(node1);
        assertEquals(17, node1.getDistributorPutLatency().getLatencyMsSum());
        assertEquals(18, node1.getDistributorPutLatency().getCount());
    }

    @Test
    public void testStorageMergeStats() throws IOException {
        String data = getJsonString();
        HostInfo hostInfo = HostInfo.createHostInfo(data);

        ContentClusterStats storageMergeStats = StorageNodeStatsBridge.generate(hostInfo.getDistributor());
        int size = 0;
        for (NodeMergeStats mergeStats : storageMergeStats) {
            assertThat(mergeStats.getCopyingIn().getBuckets(), is(2L));
            assertThat(mergeStats.getCopyingOut().getBuckets(), is(4L));
            assertThat(mergeStats.getSyncing().getBuckets(), is(1L));
            assertThat(mergeStats.getMovingOut().getBuckets(), is(3L));
            size++;
        }
        assertThat(size, is(2));
    }
}
