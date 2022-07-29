// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

public class HostInfoTest {

    private static String readDataFile(String filename) throws IOException {
        String directory = "../protocols/getnodestate/";
        Path path = Paths.get(directory + filename);
        byte[] encoded;
        encoded = Files.readAllBytes(path);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    @Test
    void testEmptyJson() {
        HostInfo hostInfo = HostInfo.createHostInfo("{}");
        assertNull(hostInfo.getVtag().getVersionOrNull());
        assertTrue(hostInfo.getDistributor().getStorageNodes().isEmpty());
        assertTrue(hostInfo.getContentNode().getResourceUsage().isEmpty());
        assertTrue(hostInfo.getMetrics().getMetrics().isEmpty());
        assertNull(hostInfo.getClusterStateVersionOrNull());
    }

    @Test
    void testExtendedJson() throws IOException {
        HostInfo hostInfo = HostInfo.createHostInfo(readDataFile("host_info.json"));
        assertEquals("5.32.76", hostInfo.getVtag().getVersionOrNull());
    }

    @Test
    void testFullSet() throws IOException {
        HostInfo hostInfo = HostInfo.createHostInfo(readDataFile("host_info.json"));
        List<StorageNode> storageNodeList = hostInfo.getDistributor().getStorageNodes();
        assertEquals(2, storageNodeList.size());
        assertEquals(0, storageNodeList.get(0).getIndex().intValue());
        List<Metrics.Metric> metrics = hostInfo.getMetrics().getMetrics();
        assertEquals(4, metrics.size());
        assertEquals(5095L, metrics.get(0).getValue().getLast().longValue());
        assertEquals("vds.datastored.alldisks.buckets", metrics.get(0).getName());
        assertEquals(129L, metrics.get(3).getValue().getLast().longValue());
        assertEquals("vds.datastored.bucket_space.buckets_total", metrics.get(3).getName());
        assertEquals(123, hostInfo.getClusterStateVersionOrNull().intValue());

        assertEquals(Optional.of(129L), hostInfo.getMetrics()
                .getValueAt("vds.datastored.bucket_space.buckets_total", Map.of("bucketSpace", "default"))
                .map(Metrics.Value::getLast));
        assertEquals(Optional.of(0L),
                hostInfo.getMetrics()
                        .getValueAt("vds.datastored.bucket_space.buckets_total", Map.of("bucketSpace", "global"))
                        .map(Metrics.Value::getLast));

        var resourceUsage = hostInfo.getContentNode().getResourceUsage();
        assertEquals(resourceUsage.size(), 2);
        assertEquals(Optional.ofNullable(resourceUsage.get("memory")).map(ResourceUsage::getUsage).orElse(0.0), 0.85, 0.00001);
        assertEquals(Optional.ofNullable(resourceUsage.get("disk")).map(ResourceUsage::getUsage).orElse(0.0), 0.6, 0.00001);
        assertEquals(Optional.ofNullable(resourceUsage.get("disk")).map(ResourceUsage::getName).orElse("missing"), "a cool disk");
        assertNull(resourceUsage.get("flux-capacitor"));
    }

    @Test
    void testSpeed() throws Exception {
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
    void testSharedFile() throws Exception {
        String json = readDataFile("distributor.json");
        HostInfo hostInfo = HostInfo.createHostInfo(json);

        List<StorageNode> storageNodeList = hostInfo.getDistributor().getStorageNodes();
        assertEquals(2, storageNodeList.size());
        Map<Integer, StorageNode> storageNodeByIndex = new TreeMap<>();
        for (StorageNode node : storageNodeList) {
            Integer index = node.getIndex();
            assertFalse(storageNodeByIndex.containsKey(index));
            storageNodeByIndex.put(index, node);
        }

        assertTrue(storageNodeByIndex.containsKey(0));
        assertEquals(0, storageNodeByIndex.get(0).getIndex().intValue());
        assertEquals(2, storageNodeByIndex.get(0).getMinCurrentReplicationFactorOrNull().intValue());

        assertTrue(storageNodeByIndex.containsKey(5));
        assertEquals(5, storageNodeByIndex.get(5).getIndex().intValue());
        assertEquals(9, storageNodeByIndex.get(5).getMinCurrentReplicationFactorOrNull().intValue());
    }
}
