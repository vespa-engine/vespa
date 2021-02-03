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
import java.util.Optional;
import java.util.TreeMap;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class HostInfoTest {

    private static String readDataFile(String filename) throws IOException {
        String directory = "../protocols/getnodestate/";
        Path path = Paths.get(directory + filename);
        byte[] encoded;
        encoded = Files.readAllBytes(path);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    @Test
    public void testEmptyJson() {
        HostInfo hostInfo = HostInfo.createHostInfo("{}");
        assertThat(hostInfo.getVtag().getVersionOrNull(), is(nullValue()));
        assertThat(hostInfo.getDistributor().getStorageNodes().size(), is(0));
        assertThat(hostInfo.getContentNode().getResourceUsage().size(), is(0));
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
        assertThat(metrics.size(), is(4));
        assertThat(metrics.get(0).getValue().getLast(), is(5095L));
        assertThat(metrics.get(0).getName(), equalTo("vds.datastored.alldisks.buckets"));
        assertThat(metrics.get(3).getValue().getLast(), is(129L));
        assertThat(metrics.get(3).getName(), equalTo("vds.datastored.bucket_space.buckets_total"));
        assertThat(hostInfo.getClusterStateVersionOrNull(), is(123));

        assertThat(hostInfo.getMetrics()
                        .getValueAt("vds.datastored.bucket_space.buckets_total", Map.of("bucketSpace", "default"))
                        .map(Metrics.Value::getLast),
                equalTo(Optional.of(129L)));
        assertThat(hostInfo.getMetrics()
                        .getValueAt("vds.datastored.bucket_space.buckets_total", Map.of("bucketSpace", "global"))
                        .map(Metrics.Value::getLast),
                equalTo(Optional.of(0L)));

        var resourceUsage = hostInfo.getContentNode().getResourceUsage();
        assertEquals(resourceUsage.size(), 2);
        assertEquals(Optional.ofNullable(resourceUsage.get("memory")).map(ResourceUsage::getUsage).orElse(0.0), 0.85, 0.00001);
        assertEquals(Optional.ofNullable(resourceUsage.get("disk")).map(ResourceUsage::getUsage).orElse(0.0), 0.6, 0.00001);
        assertEquals(Optional.ofNullable(resourceUsage.get("disk")).map(ResourceUsage::getName).orElse("missing"), "a cool disk");
        assertNull(resourceUsage.get("flux-capacitor"));
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
