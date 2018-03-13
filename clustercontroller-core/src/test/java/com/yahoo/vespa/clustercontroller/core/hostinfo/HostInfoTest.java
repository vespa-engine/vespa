// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class HostInfoTest {

    private static String readDataFile(String filename) throws IOException {
        String directory = "../protocols/getnodestate/";
        Path path = Paths.get(directory + filename);
        byte[] encoded;
        encoded = Files.readAllBytes(path);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    @Test
    public void testEmptyJson() throws IOException {
        HostInfo hostInfo = HostInfo.createHostInfo("{}");
        assertThat(hostInfo.getVtag().getVersionOrNull(), is(nullValue()));
        assertThat(hostInfo.getDistributor().getStorageNodes().size(), is(0));
        assertThat(hostInfo.getMetrics().getMetrics().size(), is(0));
        assertThat(hostInfo.getClusterStateVersionOrNull(), is(nullValue()));
    }

    @Test
    public void testExtendedJson() throws IOException {
        HostInfo hostInfo = HostInfo.createHostInfo(readDataFile("host_info.json"));
        assertThat(hostInfo.getVtag().getVersionOrNull(), is("5.32.76"));
    }

    @Test
    public void testFullSet() throws IOException {
        HostInfo hostInfo = HostInfo.createHostInfo(readDataFile("host_info.json"));
        List<StorageNode> storageNodeList = hostInfo.getDistributor().getStorageNodes();
        assertThat(storageNodeList.size(), is(2));
        assertThat(storageNodeList.get(0).getIndex(), is(0));
        List<Metrics.Metric> metrics = hostInfo.getMetrics().getMetrics();
        assertThat(metrics.size(), is(2));
        Metrics.Value value = metrics.get(0).getValue();
        assertThat(value.getLast(), is(5095L));
        assertThat(metrics.get(0).getName(), equalTo("vds.datastored.alldisks.buckets"));
        assertThat(hostInfo.getClusterStateVersionOrNull(), is(123));
    }

    @Test
    public void testSpeed() throws Exception {
        String json = readDataFile("slow_host_info.json");

        long start = 0;
        for (int x = 0; x < 100; x++) {
            if (x == 90) {
                start = System.currentTimeMillis();
            }
            HostInfo hostInfo = HostInfo.createHostInfo(json);
            // Check a value so not all code is removed by optimizer.
            if (hostInfo.getMetrics().getMetrics().size() == -1) return;
        }
        long end = System.currentTimeMillis();
        System.out.println("Should take about 1.5 ms on fast machine, actually " + (end - start) / 10. + " ms.");
    }

    @Test
    public void testSharedFile() throws Exception {
        String json = readDataFile("distributor.json");
        HostInfo hostInfo = HostInfo.createHostInfo(json);

        List<StorageNode> storageNodeList = hostInfo.getDistributor().getStorageNodes();
        assertThat(storageNodeList.size(), is(2));
        Map<Integer, StorageNode> storageNodeByIndex = new TreeMap<>();
        for (StorageNode node : storageNodeList) {
            Integer index = node.getIndex();
            assertFalse(storageNodeByIndex.containsKey(index));
            storageNodeByIndex.put(index, node);
        }

        assertTrue(storageNodeByIndex.containsKey(0));
        assertThat(storageNodeByIndex.get(0).getIndex(), is(0));
        assertThat(storageNodeByIndex.get(0).getMinCurrentReplicationFactorOrNull(), is(2));

        assertTrue(storageNodeByIndex.containsKey(5));
        assertThat(storageNodeByIndex.get(5).getIndex(), is(5));
        assertThat(storageNodeByIndex.get(5).getMinCurrentReplicationFactorOrNull(), is(9));
    }
}
