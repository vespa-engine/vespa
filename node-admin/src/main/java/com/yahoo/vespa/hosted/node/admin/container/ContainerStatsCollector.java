// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

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

/**
 * Collects CPU, memory and network statistics for a container.
 *
 * Uses same approach as runc: https://github.com/opencontainers/runc/tree/master/libcontainer/cgroups/fs
 *
 * @author mpolden
 */
class ContainerStatsCollector {

    private final FileSystem fileSystem;

    public ContainerStatsCollector(FileSystem fileSystem) {
        this.fileSystem = Objects.requireNonNull(fileSystem);
    }

    /** Collect statistics for given container ID and PID */
    public Optional<ContainerStats> collect(ContainerId containerId, int pid, String iface) {
        Cgroup cgroup = new Cgroup(fileSystem, containerId);
        try {
            ContainerStats.CpuStats cpuStats = collectCpuStats(cgroup);
            ContainerStats.MemoryStats memoryStats = collectMemoryStats(cgroup);
            Map<String, ContainerStats.NetworkStats> networkStats = Map.of(iface, collectNetworkStats(iface, pid));
            return Optional.of(new ContainerStats(networkStats, memoryStats, cpuStats));
        } catch (NoSuchFileException ignored) {
            return Optional.empty(); // Container disappeared while we collected stats
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ContainerStats.CpuStats collectCpuStats(Cgroup cgroup) throws IOException {
        List<String> cpuStatLines = Files.readAllLines(cpuStatPath(cgroup));
        long throttledActivePeriods = parseLong(cpuStatLines, "nr_periods");
        long throttledPeriods = parseLong(cpuStatLines, "nr_throttled");
        long throttledTime = parseLong(cpuStatLines, "throttled_time");
        return new ContainerStats.CpuStats(cpuCount(cgroup),
                                           systemCpuUsage().toNanos(),
                                           containerCpuUsage(cgroup).toNanos(),
                                           containerCpuUsageSystem(cgroup).toNanos(),
                                           throttledTime,
                                           throttledActivePeriods,
                                           throttledPeriods);
    }

    private ContainerStats.MemoryStats collectMemoryStats(Cgroup cgroup) throws IOException {
        long memoryLimitInBytes = parseLong(memoryLimitPath(cgroup));
        long memoryUsageInBytes = parseLong(memoryUsagePath(cgroup));
        long cachedInBytes = parseLong(memoryStatPath(cgroup), "cache");
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

    /** Number of CPUs seen by given container */
    private int cpuCount(Cgroup cgroup) throws IOException {
        return fields(Files.readString(perCpuUsagePath(cgroup))).length;
    }

    /** Returns total CPU time spent executing all the processes on this host */
    private Duration systemCpuUsage() throws IOException {
        long ticks = parseLong(fileSystem.getPath("/proc/stat"), "cpu");
        return ticksToDuration(ticks);
    }

    /** Returns total CPU time spent running all processes inside given container */
    private Duration containerCpuUsage(Cgroup cgroup) throws IOException {
        return Duration.ofNanos(parseLong(cpuUsagePath(cgroup)));
    }

    /** Returns total CPU time spent in kernel/system mode while executing processes inside given container */
    private Duration containerCpuUsageSystem(Cgroup cgroup) throws IOException {
        long ticks = parseLong(cpuacctStatPath(cgroup), "system");
        return ticksToDuration(ticks);
    }

    private long parseLong(Path path) throws IOException {
        return Long.parseLong(Files.readString(path).trim());
    }

    private long parseLong(Path path, String fieldName) throws IOException {
        return parseLong(Files.readAllLines(path), fieldName);
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

    private Path cpuacctStatPath(Cgroup cgroup) {
        return cgroup.cpuacctPath().resolve("cpuacct.stat");
    }

    private Path cpuUsagePath(Cgroup cgroup) {
        return cgroup.cpuacctPath().resolve("cpuacct.usage");
    }

    private Path perCpuUsagePath(Cgroup cgroup) {
        return cgroup.cpuacctPath().resolve("cpuacct.usage_percpu");
    }

    private Path cpuStatPath(Cgroup cgroup) {
        return cgroup.cpuacctPath().resolve("cpu.stat");
    }

    private Path memoryStatPath(Cgroup cgroup) {
        return cgroup.memoryPath().resolve("memory.stat");
    }

    private Path memoryUsagePath(Cgroup cgroup) {
        return cgroup.memoryPath().resolve("memory.usage_in_bytes");
    }

    private Path memoryLimitPath(Cgroup cgroup) {
        return cgroup.memoryPath().resolve("memory.limit_in_bytes");
    }

    private static Duration ticksToDuration(long ticks) {
        // Ideally we would read this from _SC_CLK_TCK, but then we need JNI. However, in practice this is always 100 on x86 Linux
        long ticksPerSecond = 100;
        return Duration.ofNanos((ticks * Duration.ofSeconds(1).toNanos()) / ticksPerSecond);
    }

    private static String[] fields(String s) {
        return s.split("\\s+");
    }

}
