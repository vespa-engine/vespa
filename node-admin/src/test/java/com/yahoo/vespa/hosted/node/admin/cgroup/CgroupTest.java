// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.cgroup;

import com.yahoo.vespa.hosted.node.admin.container.ContainerId;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContextImpl;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.hosted.node.admin.cgroup.CpuController.StatField.SYSTEM_USAGE_USEC;
import static com.yahoo.vespa.hosted.node.admin.cgroup.CpuController.StatField.THROTTLED_PERIODS;
import static com.yahoo.vespa.hosted.node.admin.cgroup.CpuController.StatField.THROTTLED_TIME_USEC;
import static com.yahoo.vespa.hosted.node.admin.cgroup.CpuController.StatField.TOTAL_PERIODS;
import static com.yahoo.vespa.hosted.node.admin.cgroup.CpuController.StatField.TOTAL_USAGE_USEC;
import static com.yahoo.vespa.hosted.node.admin.cgroup.CpuController.StatField.USER_USAGE_USEC;
import static com.yahoo.vespa.hosted.node.admin.cgroup.CpuController.sharesToWeight;
import static com.yahoo.vespa.hosted.node.admin.cgroup.CpuController.weightToShares;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author freva
 */
public class CgroupTest {

    private static final ContainerId containerId = new ContainerId("4aec78cc");

    private final FileSystem fileSystem = TestFileSystem.create();
    private final Cgroup containerCgroup = Cgroup.root(fileSystem).resolveContainer(containerId);
    private final CpuController containerCpu = containerCgroup.cpu();
    private final NodeAgentContext context = NodeAgentContextImpl.builder("node123.yahoo.com").fileSystem(fileSystem).build();
    private final UnixPath cgroupRoot = new UnixPath(fileSystem.getPath("/sys/fs/cgroup/machine.slice/libpod-4aec78cc.scope/container")).createDirectories();

    @Test
    public void updates_cpu_quota_and_period() {
        assertEquals(Optional.empty(), containerCgroup.cpu().readMax());

        cgroupRoot.resolve("cpu.max").writeUtf8File("max 100000\n");
        assertEquals(Optional.of(new CpuController.Max(Size.max(), 100000)), containerCpu.readMax());

        cgroupRoot.resolve("cpu.max").writeUtf8File("456 123456\n");
        assertEquals(Optional.of(new CpuController.Max(Size.from(456), 123456)), containerCpu.readMax());

        containerCgroup.cpu().updateMax(context, 456, 123456);

        assertTrue(containerCgroup.cpu().updateMax(context, 654, 123456));
        assertEquals(Optional.of(new CpuController.Max(Size.from(654), 123456)), containerCpu.readMax());
        assertEquals("654 123456\n", cgroupRoot.resolve("cpu.max").readUtf8File());

        assertTrue(containerCgroup.cpu().updateMax(context, -1, 123456));
        assertEquals(Optional.of(new CpuController.Max(Size.max(), 123456)), containerCpu.readMax());
        assertEquals("max 123456\n", cgroupRoot.resolve("cpu.max").readUtf8File());
    }

    @Test
    public void updates_cpu_shares() {
        assertEquals(Optional.empty(), containerCgroup.cpu().readShares());

        cgroupRoot.resolve("cpu.weight").writeUtf8File("1\n");
        assertEquals(Optional.of(2), containerCgroup.cpu().readShares());

        assertFalse(containerCgroup.cpu().updateShares(context, 2));

        assertTrue(containerCgroup.cpu().updateShares(context, 12345));
        assertEquals(Optional.of(12323), containerCgroup.cpu().readShares());
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
                TOTAL_PERIODS, 12465L, THROTTLED_PERIODS, 25L, THROTTLED_TIME_USEC, 14256L), containerCgroup.cpu().readStats());
    }

    @Test
    public void reads_memory_metrics() throws IOException {
        cgroupRoot.resolve("memory.current").writeUtf8File("2525093888\n");
        assertEquals(2525093888L, containerCgroup.memory().readCurrent().value());

        cgroupRoot.resolve("memory.max").writeUtf8File("4322885632\n");
        assertEquals(4322885632L, containerCgroup.memory().readMax().value());

        cgroupRoot.resolve("memory.stat").writeUtf8File("anon 3481600\n" +
                "file 69206016\n" +
                "kernel_stack 73728\n" +
                "slab 3552304\n" +
                "percpu 262336\n" +
                "sock 73728\n" +
                "shmem 8380416\n" +
                "file_mapped 1081344\n" +
                "file_dirty 135168\n");
        assertEquals(69206016L, containerCgroup.memory().readFileSystemCache().value());
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
