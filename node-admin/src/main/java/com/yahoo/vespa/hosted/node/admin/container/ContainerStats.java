// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CPU, memory and network statistics collected from a container.
 *
 * @author freva
 */
public class ContainerStats {

    private final Map<String, NetworkStats> networkStatsByInterface;
    private final MemoryStats memoryStats;
    private final CpuStats cpuStats;

    public ContainerStats(Map<String, NetworkStats> networkStatsByInterface, MemoryStats memoryStats, CpuStats cpuStats) {
        this.networkStatsByInterface = new LinkedHashMap<>(Objects.requireNonNull(networkStatsByInterface));
        this.memoryStats = Objects.requireNonNull(memoryStats);
        this.cpuStats = Objects.requireNonNull(cpuStats);
    }

    public Map<String, NetworkStats> getNetworks() {
        return Collections.unmodifiableMap(networkStatsByInterface);
    }

    public MemoryStats getMemoryStats() {
        return memoryStats;
    }

    public CpuStats getCpuStats() {
        return cpuStats;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerStats that = (ContainerStats) o;
        return networkStatsByInterface.equals(that.networkStatsByInterface) && memoryStats.equals(that.memoryStats) && cpuStats.equals(that.cpuStats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkStatsByInterface, memoryStats, cpuStats);
    }

    /**
     * Statistics for network usage
     *
     * @param rxBytes   received bytes
     * @param rxDropped received bytes, which were dropped
     * @param rxErrors  received errors
     * @param txBytes   transmitted bytes
     * @param txDropped transmitted bytes, which were dropped
     * @param txErrors  transmission errors
     */
    public record NetworkStats(long rxBytes, long rxDropped, long rxErrors, long txBytes, long txDropped, long txErrors) {}

    /**
     * Statistics for memory usage
     *
     * @param cache memory used by cache in bytes
     * @param usage memory usage in bytes
     * @param limit memory limit in bytes
     */
    public record MemoryStats(long cache, long usage, long limit) {}

    /**
     * Statistics for CPU usage
     *
     * @param onlineCpus              CPU cores
     * @param systemCpuUsage          Total CPU time (in µs) spent executing all the processes on this host
     * @param totalUsage              Total CPU time (in µs) spent running all the processes in this container
     * @param usageInKernelMode       Total CPU time (in µs) spent in kernel mode while executing processes in this container
     * @param throttledTime           Total CPU time (in µs) processes in this container were throttled for
     * @param throttlingActivePeriods Number of periods with throttling enabled for this container
     * @param throttledPeriods        Number of periods this container hit the throttling limit
     */
    public record CpuStats(int onlineCpus,
                           long systemCpuUsage,
                           long totalUsage,
                           long usageInKernelMode,
                           long throttledTime,
                           long throttlingActivePeriods,
                           long throttledPeriods) {}

}
