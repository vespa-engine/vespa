// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.vespa.hosted.node.admin.cgroup.Cgroup;
import com.yahoo.vespa.hosted.node.admin.cgroup.CpuController;
import com.yahoo.vespa.hosted.node.admin.cgroup.Size;
import com.yahoo.vespa.hosted.node.admin.cgroup.MemoryController;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixUser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Collects CPU, GPU, memory and network statistics for a container.
 *
 * Uses same approach as runc: https://github.com/opencontainers/runc/tree/master/libcontainer/cgroups/fs
 *
 * @author mpolden
 */
class ContainerStatsCollector {

    private final ContainerEngine containerEngine;
    private final FileSystem fileSystem;
    private final Cgroup rootCgroup;
    private final int onlineCpus;

    ContainerStatsCollector(ContainerEngine containerEngine, Cgroup rootCgroup, FileSystem fileSystem) {
        this(containerEngine, rootCgroup, fileSystem, Runtime.getRuntime().availableProcessors());
    }

    ContainerStatsCollector(ContainerEngine containerEngine, Cgroup rootCgroup, FileSystem fileSystem, int onlineCpus) {
        this.containerEngine = Objects.requireNonNull(containerEngine);
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.rootCgroup = Objects.requireNonNull(rootCgroup);
        this.onlineCpus = onlineCpus;
    }

    /** Collect statistics for given container ID and PID */
    public Optional<ContainerStats> collect(NodeAgentContext context, ContainerId containerId, int pid, String iface) {
        try {
            ContainerStats.CpuStats cpuStats = collectCpuStats(containerId);
            ContainerStats.MemoryStats memoryStats = collectMemoryStats(containerId);
            Map<String, ContainerStats.NetworkStats> networkStats = Map.of(iface, collectNetworkStats(iface, pid));
            List<ContainerStats.GpuStats> gpuStats = collectGpuStats(context);
            return Optional.of(new ContainerStats(networkStats, memoryStats, cpuStats, gpuStats));
        } catch (NoSuchFileException ignored) {
            return Optional.empty(); // Container disappeared while we collected stats
        } catch (UncheckedIOException e) {
            if (e.getCause() != null && e.getCause() instanceof  NoSuchFileException)
                return Optional.empty();
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<ContainerStats.GpuStats> collectGpuStats(NodeAgentContext context) {
        boolean hasGpu = Files.exists(fileSystem.getPath("/dev/nvidia0"));
        if (!hasGpu) {
            return List.of();
        }
        Stream<String> lines = containerEngine.execute(context, UnixUser.ROOT, Duration.ofSeconds(5),
                                                       "nvidia-smi",
                                                       "--query-gpu=index,utilization.gpu,memory.total,memory.free",
                                                       "--format=csv,noheader,nounits")
                                              .getOutputLinesStream();
        return lines.map(ContainerStatsCollector::parseGpuStats).toList();
    }

    private static ContainerStats.GpuStats parseGpuStats(String s) {
        String[] fields = fields(s, ",\\s*");
        if (fields.length < 4) throw new IllegalArgumentException("Could not parse GPU stats from '" + s + "'");
        int deviceNumber = Integer.parseInt(fields[0]);
        int loadPercentage = Integer.parseInt(fields[1]);
        long mega = 2 << 19;
        long memoryTotalBytes = Long.parseLong(fields[2]) * mega;
        long memoryFreeBytes = Long.parseLong(fields[3]) * mega;
        long memoryUsedBytes = memoryTotalBytes - memoryFreeBytes;
        return new ContainerStats.GpuStats(deviceNumber, loadPercentage, memoryTotalBytes, memoryUsedBytes);
    }

    private ContainerStats.CpuStats collectCpuStats(ContainerId containerId) throws IOException {
        Map<CpuController.StatField, Long> cpuStats = rootCgroup.resolveContainer(containerId).cpu().readStats();
        return new ContainerStats.CpuStats(onlineCpus,
                                           systemCpuUsage(),
                                           cpuStats.get(CpuController.StatField.TOTAL_USAGE_USEC),
                                           cpuStats.get(CpuController.StatField.SYSTEM_USAGE_USEC),
                                           cpuStats.get(CpuController.StatField.THROTTLED_TIME_USEC),
                                           cpuStats.get(CpuController.StatField.TOTAL_PERIODS),
                                           cpuStats.get(CpuController.StatField.THROTTLED_PERIODS));
    }

    private ContainerStats.MemoryStats collectMemoryStats(ContainerId containerId) throws IOException {
        MemoryController memoryController = rootCgroup.resolveContainer(containerId).memory();
        Size max = memoryController.readMax();
        long memoryUsageInBytes = memoryController.readCurrent().value();
        long cachedInBytes = memoryController.readFileSystemCache().value();
        return new ContainerStats.MemoryStats(cachedInBytes, memoryUsageInBytes, max.isMax() ? -1 : max.value());
    }

    private ContainerStats.NetworkStats collectNetworkStats(String iface, int containerPid) throws IOException {
        for (var line : Files.readAllLines(netDevPath(containerPid))) {
            String[] fields = fields(line.trim());
            if (fields.length < 17 || !fields[0].equals(iface + ":")) continue;

            long rxBytes = Long.parseLong(fields[1]);
            long rxErrors = Long.parseLong(fields[3]);
            long rxDropped = Long.parseLong(fields[4]);

            long txBytes = Long.parseLong(fields[9]);
            long txErrors = Long.parseLong(fields[11]);
            long txDropped = Long.parseLong(fields[12]);

            return new ContainerStats.NetworkStats(rxBytes, rxDropped, rxErrors, txBytes, txDropped, txErrors);
        }
        throw new IllegalArgumentException("No statistics found for interface " + iface);
    }

    /** Returns total CPU time in µs spent executing all the processes on this host */
    private long systemCpuUsage() throws IOException {
        long ticks = parseLong(Files.readAllLines(fileSystem.getPath("/proc/stat")), "cpu");
        return userHzToMicroSeconds(ticks);
    }

    private long parseLong(List<String> lines, String fieldName) {
        long value = 0;
        for (var line : lines) {
            String[] fields = fields(line);
            if (fields.length < 2 || !fields[0].equals(fieldName)) continue;
            for (int i = 1; i < fields.length; i++) {
                value += Long.parseLong(fields[i]);
            }
            break;
        }
        return value;
    }

    private Path netDevPath(int containerPid) {
        return fileSystem.getPath("/proc/" + containerPid + "/net/dev");
    }

    static long userHzToMicroSeconds(long ticks) {
        // Ideally we would read this from _SC_CLK_TCK, but then we need JNI. However, in practice this is always 100 on x86 Linux
        return ticks * 10_000;
    }

    private static String[] fields(String s) {
        return fields(s, "\\s+");
    }

    private static String[] fields(String s, String regex) {
        return s.trim().split(regex);
    }

}
