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
import static com.yahoo.vespa.hosted.node.admin.container.CGroupV2.sharesToWeight;
import static com.yahoo.vespa.hosted.node.admin.container.CGroupV2.weightToShares;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author freva
 */
public class CGroupV2Test {

    private static final ContainerId containerId = new ContainerId("4aec78cc");

    private final FileSystem fileSystem = TestFileSystem.create();
    private final CGroup cgroup = new CGroupV2(fileSystem);
    private final NodeAgentContext context = NodeAgentContextImpl.builder("node123.yahoo.com").fileSystem(fileSystem).build();
    private final UnixPath cgroupRoot = new UnixPath(fileSystem.getPath("/sys/fs/cgroup/machine.slice/libpod-4aec78cc.scope/container")).createDirectories();

    @Test
    public void updates_cpu_quota_and_period() {
        assertEquals(Optional.empty(), cgroup.cpuQuotaPeriod(containerId));

        cgroupRoot.resolve("cpu.max").writeUtf8File("max 100000\n");
        assertEquals(Optional.of(new Pair<>(-1, 100000)), cgroup.cpuQuotaPeriod(containerId));

        cgroupRoot.resolve("cpu.max").writeUtf8File("456 123456\n");
        assertEquals(Optional.of(new Pair<>(456, 123456)), cgroup.cpuQuotaPeriod(containerId));

        assertFalse(cgroup.updateCpuQuotaPeriod(context, containerId, 456, 123456));

        assertTrue(cgroup.updateCpuQuotaPeriod(context, containerId, 654, 123456));
        assertEquals(Optional.of(new Pair<>(654, 123456)), cgroup.cpuQuotaPeriod(containerId));
        assertEquals("654 123456", cgroupRoot.resolve("cpu.max").readUtf8File());

        assertTrue(cgroup.updateCpuQuotaPeriod(context, containerId, -1, 123456));
        assertEquals(Optional.of(new Pair<>(-1, 123456)), cgroup.cpuQuotaPeriod(containerId));
        assertEquals("max 123456", cgroupRoot.resolve("cpu.max").readUtf8File());
    }

    @Test
    public void updates_cpu_shares() {
        assertEquals(OptionalInt.empty(), cgroup.cpuShares(containerId));

        cgroupRoot.resolve("cpu.weight").writeUtf8File("1\n");
        assertEquals(OptionalInt.of(2), cgroup.cpuShares(containerId));

        assertFalse(cgroup.updateCpuShares(context, containerId, 2));

        assertTrue(cgroup.updateCpuShares(context, containerId, 12345));
        assertEquals(OptionalInt.of(12323), cgroup.cpuShares(containerId));
    }

    @Test
    public void reads_cpu_stats() throws IOException {
        cgroupRoot.resolve("cpu.stat").writeUtf8File("usage_usec 17794243\n" +
                "user_usec 16099205\n" +
                "system_usec 1695038\n" +
                "nr_periods 12465\n" +
                "nr_throttled 25\n" +
                "throttled_usec 14256\n");

        assertEquals(Map.of(TOTAL_USAGE_USEC, 17794243L, USER_USAGE_USEC, 16099205L, SYSTEM_USAGE_USEC, 1695038L,
                TOTAL_PERIODS, 12465L, THROTTLED_PERIODS, 25L, THROTTLED_TIME_USEC, 14256L), cgroup.cpuStats(containerId));
    }

    @Test
    public void reads_memory_metrics() throws IOException {
        cgroupRoot.resolve("memory.current").writeUtf8File("2525093888\n");
        assertEquals(2525093888L, cgroup.memoryUsageInBytes(containerId));

        cgroupRoot.resolve("memory.max").writeUtf8File("4322885632\n");
        assertEquals(4322885632L, cgroup.memoryLimitInBytes(containerId));

        cgroupRoot.resolve("memory.stat").writeUtf8File("anon 3481600\n" +
                "file 69206016\n" +
                "kernel_stack 73728\n" +
                "slab 3552304\n" +
                "percpu 262336\n" +
                "sock 73728\n" +
                "shmem 8380416\n" +
                "file_mapped 1081344\n" +
                "file_dirty 135168\n");
        assertEquals(69206016L, cgroup.memoryCacheInBytes(containerId));
    }

    @Test
    public void shares_to_weight_and_back_is_stable() {
        for (int i = 2; i <= 262144; i++) {
            int originalShares = i; // Must be effectively final to use in lambda :(
            int roundTripShares = weightToShares(sharesToWeight(i));
            int diff = i - roundTripShares;
            assertTrue(diff >= 0 && diff <= 27, // ~26.2 shares / weight
                    () -> "Original shares: " + originalShares + ", round trip shares: " + roundTripShares + ", diff: " + diff);
        }
    }
}
