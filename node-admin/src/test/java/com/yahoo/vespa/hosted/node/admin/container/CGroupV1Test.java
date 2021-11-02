// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.yahoo.vespa.hosted.node.admin.container.CGroup.CpuStatField.SYSTEM_USAGE_USEC;
import static com.yahoo.vespa.hosted.node.admin.container.CGroup.CpuStatField.THROTTLED_PERIODS;
import static com.yahoo.vespa.hosted.node.admin.container.CGroup.CpuStatField.THROTTLED_TIME_USEC;
import static com.yahoo.vespa.hosted.node.admin.container.CGroup.CpuStatField.TOTAL_PERIODS;
import static com.yahoo.vespa.hosted.node.admin.container.CGroup.CpuStatField.TOTAL_USAGE_USEC;
import static com.yahoo.vespa.hosted.node.admin.container.CGroup.CpuStatField.USER_USAGE_USEC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author freva
 */
public class CGroupV1Test {

    private static final ContainerId containerId = new ContainerId("4aec78cc");

    private final FileSystem fileSystem = TestFileSystem.create();
    private final CGroup cgroup = new CGroupV1(fileSystem);
    private final NodeAgentContext context = NodeAgentContextImpl.builder("node123.yahoo.com").fileSystem(fileSystem).build();

    @Test
    public void updates_cpu_quota_and_period() {
        assertEquals(Optional.empty(), cgroup.cpuQuotaPeriod(containerId));

        UnixPath cpu = new UnixPath(fileSystem.getPath("/sys/fs/cgroup/cpu/machine.slice/libpod-4aec78cc.scope")).createDirectories();
        cpu.resolve("cpu.cfs_period_us").writeUtf8File("123456\n");
        cpu.resolve("cpu.cfs_quota_us").writeUtf8File("-1\n");
        assertEquals(Optional.of(new Pair<>(-1, 123456)), cgroup.cpuQuotaPeriod(containerId));

        cpu.resolve("cpu.cfs_quota_us").writeUtf8File("456\n");
        assertEquals(Optional.of(new Pair<>(456, 123456)), cgroup.cpuQuotaPeriod(containerId));

        assertFalse(cgroup.updateCpuQuotaPeriod(context, containerId, 456, 123456));

        assertTrue(cgroup.updateCpuQuotaPeriod(context, containerId, 654, 123456));
        assertEquals(Optional.of(new Pair<>(654, 123456)), cgroup.cpuQuotaPeriod(containerId));
    }

    @Test
    public void updates_cpu_shares() {
        assertEquals(OptionalInt.empty(), cgroup.cpuShares(containerId));

        UnixPath cpuPath = new UnixPath(fileSystem.getPath("/sys/fs/cgroup/cpu/machine.slice/libpod-4aec78cc.scope")).createDirectories();
        cpuPath.resolve("cpu.shares").writeUtf8File("987\n");
        assertEquals(OptionalInt.of(987), cgroup.cpuShares(containerId));

        assertFalse(cgroup.updateCpuShares(context, containerId, 987));

        assertTrue(cgroup.updateCpuShares(context, containerId, 789));
        assertEquals(OptionalInt.of(789), cgroup.cpuShares(containerId));
    }

    @Test
    public void reads_cpu_stats() throws IOException {
        UnixPath cpuacctPath = new UnixPath(fileSystem.getPath("/sys/fs/cgroup/cpuacct/machine.slice/libpod-4aec78cc.scope")).createDirectories();
        cpuacctPath.resolve("cpuacct.usage").writeUtf8File("91623711445\n");
        cpuacctPath.resolve("cpuacct.stat").writeUtf8File("user 7463\n" +
                "system 1741\n");
        cpuacctPath.resolve("cpu.stat").writeUtf8File("nr_periods 2361\n" +
                "nr_throttled 342\n" +
                "throttled_time 131033468519\n");

        assertEquals(Map.of(TOTAL_USAGE_USEC, 91623711L, SYSTEM_USAGE_USEC, 17410000L, USER_USAGE_USEC, 74630000L,
                TOTAL_PERIODS, 2361L, THROTTLED_PERIODS, 342L, THROTTLED_TIME_USEC, 131033468L), cgroup.cpuStats(containerId));
    }

    @Test
    public void reads_memory_metrics() throws IOException {
        UnixPath memoryPath = new UnixPath(fileSystem.getPath("/sys/fs/cgroup/memory/machine.slice/libpod-4aec78cc.scope")).createDirectories();
        memoryPath.resolve("memory.usage_in_bytes").writeUtf8File("2525093888\n");
        assertEquals(2525093888L, cgroup.memoryUsageInBytes(containerId));

        memoryPath.resolve("memory.limit_in_bytes").writeUtf8File("4322885632\n");
        assertEquals(4322885632L, cgroup.memoryLimitInBytes(containerId));

        memoryPath.resolve("memory.stat").writeUtf8File("cache 296828928\n" +
                "rss 2152587264\n" +
                "rss_huge 1107296256\n" +
                "shmem 135168\n" +
                "mapped_file 270336\n");
        assertEquals(296828928L, cgroup.memoryCacheInBytes(containerId));
    }
}
