// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Collects CPU, memory and network statistics for a container.
 *
 * Uses same approach as runc: https://github.com/opencontainers/runc/tree/master/libcontainer/cgroups/fs
 *
 * @author mpolden
 */
class ContainerStatsCollector {

    private final CGroup cgroup;
    private final FileSystem fileSystem;
    private final int onlineCpus;

    ContainerStatsCollector(CGroup cgroup, FileSystem fileSystem) {
        this(cgroup, fileSystem, Runtime.getRuntime().availableProcessors());
    }

    ContainerStatsCollector(CGroup cgroup, FileSystem fileSystem, int onlineCpus) {
        this.cgroup = Objects.requireNonNull(cgroup);
        this.fileSystem = Objects.requireNonNull(fileSystem);
        this.onlineCpus = onlineCpus;
    }

    /** Collect statistics for given container ID and PID */
    public Optional<ContainerStats> collect(ContainerId containerId, int pid, String iface) {
        try {
            ContainerStats.CpuStats cpuStats = collectCpuStats(containerId);
            ContainerStats.MemoryStats memoryStats = collectMemoryStats(containerId);
            Map<String, ContainerStats.NetworkStats> networkStats = Map.of(iface, collectNetworkStats(iface, pid));
            return Optional.of(new ContainerStats(networkStats, memoryStats, cpuStats));
        } catch (NoSuchFileException ignored) {
            return Optional.empty(); // Container disappeared while we collected stats
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ContainerStats.CpuStats collectCpuStats(ContainerId containerId) throws IOException {
        Map<CGroup.CpuStatField, Long> cpuStats = cgroup.cpuStats(containerId);
        return new ContainerStats.CpuStats(onlineCpus,
                                           systemCpuUsage(),
                                           cpuStats.get(CGroup.CpuStatField.TOTAL_USAGE_USEC),
                                           cpuStats.get(CGroup.CpuStatField.SYSTEM_USAGE_USEC),
                                           cpuStats.get(CGroup.CpuStatField.THROTTLED_TIME_USEC),
                                           cpuStats.get(CGroup.CpuStatField.TOTAL_PERIODS),
                                           cpuStats.get(CGroup.CpuStatField.THROTTLED_PERIODS));
    }

    private ContainerStats.MemoryStats collectMemoryStats(ContainerId containerId) throws IOException {
        long memoryLimitInBytes = cgroup.memoryLimitInBytes(containerId);
        long memoryUsageInBytes = cgroup.memoryUsageInBytes(containerId);
        long cachedInBytes = cgroup.memoryCacheInBytes(containerId);
        return new ContainerStats.MemoryStats(cachedInBytes, memoryUsageInBytes, memoryLimitInBytes);
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
        return s.split("\\s+");
    }

}
