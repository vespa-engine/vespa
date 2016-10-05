package com.yahoo.vespa.hosted.node.admin.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.dockerapi.ContainerStatsImpl;
import com.yahoo.vespa.hosted.dockerapi.Docker;
import com.yahoo.vespa.hosted.node.maintenance.DeleteOldAppDataTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author dybis
 */
public class StorageMaintainerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testDiskUsed() throws IOException, InterruptedException {
        int writeSize = 10000;
        DeleteOldAppDataTest.writeNBytesToFile(folder.newFile(), writeSize);

        StorageMaintainer storageMaintainer = new StorageMaintainer();
        long usedBytes = storageMaintainer.getDiscUsedInBytes(folder.getRoot());
        if (usedBytes * 4 < writeSize || usedBytes > writeSize * 4)
            fail("Used bytes is " + usedBytes + ", but wrote " + writeSize + " bytes, not even close.");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetRelevantMetrics() throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        ClassLoader classLoader = getClass().getClassLoader();
        File statsFile = new File(classLoader.getResource("docker.stats.json").getFile());
        Map<String, Object> dockerStats = objectMapper.readValue(statsFile, Map.class);

        Map<String, Object> networks = (Map<String, Object>) dockerStats.get("networks");
        Map<String, Object> cpu_stats = (Map<String, Object>) dockerStats.get("cpu_stats");
        Map<String, Object> memory_stats = (Map<String, Object>) dockerStats.get("memory_stats");
        Map<String, Object> blkio_stats = (Map<String, Object>) dockerStats.get("blkio_stats");
        Docker.ContainerStats stats = new ContainerStatsImpl(networks, cpu_stats, memory_stats, blkio_stats);

        Map<String, Object> expectedRelevantStats = new HashMap<>();
        expectedRelevantStats.put("node.cpu.throttled_time", 4523);
        expectedRelevantStats.put("node.cpu.system_cpu_usage", 5876882680000000L);
        expectedRelevantStats.put("node.cpu.total_usage", 332131205198L);

        expectedRelevantStats.put("node.memory.limit", 4294967296L);
        expectedRelevantStats.put("node.memory.usage", 1752707072);

        expectedRelevantStats.put("node.network.ipv4.bytes_rcvd", 19499270);
        expectedRelevantStats.put("node.network.ipv4.bytes_sent", 20303455);
        expectedRelevantStats.put("node.network.ipv6.bytes_rcvd", 3245766);
        expectedRelevantStats.put("node.network.ipv6.bytes_sent", 54246745);

        assertEquals(expectedRelevantStats, StorageMaintainer.getRelevantMetricsFromDockerStats(stats));
    }
}