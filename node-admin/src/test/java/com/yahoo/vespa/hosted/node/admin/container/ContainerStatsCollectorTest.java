// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.process.TestTerminal;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.hosted.node.admin.container.CGroupV2.CpuStatField.SYSTEM_USAGE_USEC;
import static com.yahoo.vespa.hosted.node.admin.container.CGroupV2.CpuStatField.THROTTLED_PERIODS;
import static com.yahoo.vespa.hosted.node.admin.container.CGroupV2.CpuStatField.THROTTLED_TIME_USEC;
import static com.yahoo.vespa.hosted.node.admin.container.CGroupV2.CpuStatField.TOTAL_PERIODS;
import static com.yahoo.vespa.hosted.node.admin.container.CGroupV2.CpuStatField.TOTAL_USAGE_USEC;
import static com.yahoo.vespa.hosted.node.admin.container.CGroupV2.CpuStatField.USER_USAGE_USEC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author mpolden
 */
public class ContainerStatsCollectorTest {

    private final TestTerminal testTerminal = new TestTerminal();
    private final ContainerEngineMock containerEngine = new ContainerEngineMock(testTerminal);
    private final FileSystem fileSystem = TestFileSystem.create();
    private final CGroupV2 cgroup = mock(CGroupV2.class);
    private final NodeAgentContext context = NodeAgentContextImpl.builder(NodeSpec.Builder.testSpec("c1").build())
                                                                 .fileSystem(TestFileSystem.create())
                                                                 .build();

    @Test
    void collect() throws Exception {
        ContainerStatsCollector collector = new ContainerStatsCollector(containerEngine, cgroup, fileSystem, 24);
        ContainerId containerId = new ContainerId("id1");
        int containerPid = 42;
        assertTrue(collector.collect(context, containerId, containerPid, "eth0").isEmpty(), "No stats found");

        mockMemoryStats(containerId);
        mockCpuStats(containerId);
        mockNetworkStats(containerPid);

        Optional<ContainerStats> stats = collector.collect(context, containerId, containerPid, "eth0");
        assertTrue(stats.isPresent());
        assertEquals(new ContainerStats.CpuStats(24, 6049374780000L, 691675615472L,
                        262190000000L, 3L, 1L, 2L),
                stats.get().cpuStats());
        assertEquals(new ContainerStats.MemoryStats(470790144L, 1228017664L, 2147483648L),
                stats.get().memoryStats());
        assertEquals(Map.of("eth0", new ContainerStats.NetworkStats(22280813L, 4L, 3L,
                        19859383L, 6L, 5L)),
                stats.get().networks());
        assertEquals(List.of(), stats.get().gpuStats());

        mockGpuStats();
        stats = collector.collect(context, containerId, containerPid, "eth0");
        assertTrue(stats.isPresent());
        assertEquals(List.of(new ContainerStats.GpuStats(0, 35, 16106127360L, 6144655360L),
                             new ContainerStats.GpuStats(1, 67, 32212254720L, 19314769920L)),
                     stats.get().gpuStats());
    }

    private void mockGpuStats() throws IOException {
        Path devPath = fileSystem.getPath("/dev");
        Files.createDirectories(devPath);
        Files.createFile(devPath.resolve("nvidia0"));
        testTerminal.expectCommand("nvidia-smi --query-gpu=index,utilization.gpu,memory.total,memory.free --format=csv,noheader,nounits 2>&1", 0,
                                   """
    0, 35, 15360, 9500
    1, 67, 30720, 12300
    """);
    }

    private void mockNetworkStats(int pid) {
        UnixPath dev = new UnixPath(fileSystem.getPath("/proc/" + pid + "/net/dev"));
        dev.createParents().writeUtf8File("Inter-|   Receive                                                |  Transmit\n" +
                               " face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed\n" +
                               "    lo: 36289258  149700    0    0    0     0          0         0 36289258  149700    0    0    0     0       0          0\n" +
                               "  eth0: 22280813  118083    3    4    0     0          0         0 19859383  115415    5    6    0     0       0          0\n");
    }

    private void mockMemoryStats(ContainerId containerId) throws IOException {
        when(cgroup.memoryUsageInBytes(eq(containerId))).thenReturn(1228017664L);
        when(cgroup.memoryLimitInBytes(eq(containerId))).thenReturn(2147483648L);
        when(cgroup.memoryCacheInBytes(eq(containerId))).thenReturn(470790144L);
    }

    private void mockCpuStats(ContainerId containerId) throws IOException {
        UnixPath proc = new UnixPath(fileSystem.getPath("/proc"));
        proc.createDirectories();

        when(cgroup.cpuStats(eq(containerId))).thenReturn(Map.of(
                TOTAL_USAGE_USEC, 691675615472L, SYSTEM_USAGE_USEC, 262190000000L, USER_USAGE_USEC, 40900L,
                TOTAL_PERIODS, 1L, THROTTLED_PERIODS, 2L, THROTTLED_TIME_USEC, 3L));

        proc.resolve("stat").writeUtf8File("cpu  7991366 978222 2346238 565556517 1935450 25514479 615206 0 0 0\n" +
                                                "cpu0 387906 61529 99088 23516506 42258 1063359 29882 0 0 0\n" +
                                                "cpu1 271253 49383 86149 23655234 41703 1061416 31885 0 0 0\n" +
                                                "cpu2 349420 50987 93560 23571695 59437 1051977 24461 0 0 0\n" +
                                                "cpu3 328107 50628 93406 23605135 44378 1048549 30199 0 0 0\n" +
                                                "cpu4 267474 50404 99253 23606041 113094 1038572 26494 0 0 0\n" +
                                                "cpu5 309584 50677 94284 23550372 132616 1033661 29436 0 0 0\n" +
                                                "cpu6 477926 56888 121251 23367023 83121 1074930 28818 0 0 0\n" +
                                                "cpu7 335335 29350 106130 23551107 95606 1066394 26156 0 0 0\n" +
                                                "cpu8 323678 28629 99171 23586501 82183 1064708 25403 0 0 0\n" +
                                                "cpu9 329805 27516 98538 23579458 89235 1061561 25140 0 0 0\n" +
                                                "cpu10 291536 26455 93934 23642345 81282 1049736 25228 0 0 0\n" +
                                                "cpu11 271103 25302 90630 23663641 85711 1048781 24291 0 0 0\n" +
                                                "cpu12 323634 63392 100406 23465340 132684 1089157 28319 0 0 0\n" +
                                                "cpu13 348085 49568 100772 23490388 114190 1079474 20948 0 0 0\n" +
                                                "cpu14 310712 51208 90461 23547980 101601 1071940 26712 0 0 0\n" +
                                                "cpu15 360405 52754 94620 23524878 79851 1062050 26836 0 0 0\n" +
                                                "cpu16 367893 52141 98074 23541314 57500 1058968 25242 0 0 0\n" +
                                                "cpu17 412756 51486 101592 23515056 47653 1044874 27467 0 0 0\n" +
                                                "cpu18 287307 25478 106011 23599505 79848 1089812 23160 0 0 0\n" +
                                                "cpu19 275001 24421 98338 23628694 79675 1084074 22083 0 0 0\n" +
                                                "cpu20 288038 24805 94432 23629908 74735 1078501 21915 0 0 0\n" +
                                                "cpu21 295373 25017 91344 23628585 75282 1071019 22026 0 0 0\n" +
                                                "cpu22 326739 25588 90385 23608217 69186 1068494 21108 0 0 0\n" +
                                                "cpu23 452284 24602 104397 23481583 72612 1052462 21985 0 0 0\n" +
                                                "intr 6645352968 64 0 0 0 1481 0 0 0 1 0 0 0 0 0 0 0 39 0 0 0 0 0 0 37 0 0 0 0 0 0 0 0 4334106 1 6949071 5814662 5415344 6939471 6961483 6358810 5271953 6718644 0 126114 126114 126114 126114 126114 126114 126114 126114 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0\n" +
                                                "ctxt 2495530303\n" +
                                                "btime 1611928223\n" +
                                                "processes 4839481\n" +
                                                "procs_running 4\n" +
                                                "procs_blocked 0\n" +
                                                "softirq 2202631388 4 20504999 46734 54405637 4330276 0 6951 1664780312 10130 458546345\n");
    }

}
