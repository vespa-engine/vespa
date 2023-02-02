// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import ai.vespa.validation.Validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CPU, GPU, memory and network statistics collected from a container.
 *
 * @author freva
 */
public record ContainerStats(Map<String, NetworkStats> networks,
                             MemoryStats memoryStats,
                             CpuStats cpuStats,
                             List<GpuStats> gpuStats) {

    public ContainerStats(Map<String, NetworkStats> networks, MemoryStats memoryStats, CpuStats cpuStats, List<GpuStats> gpuStats) {
        this.networks = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(networks)));
        this.memoryStats = Objects.requireNonNull(memoryStats);
        this.cpuStats = Objects.requireNonNull(cpuStats);
        this.gpuStats = List.copyOf(Objects.requireNonNull(gpuStats));
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

    /**
     * GPU statistics
     *
     * @param deviceNumber     GPU device number
     * @param loadPercentage   Load/utilization in %
     * @param memoryTotalBytes Total memory, in bytes
     * @param memoryUsedBytes  Memory used, in bytes
     */
    public record GpuStats(int deviceNumber, int loadPercentage, long memoryTotalBytes, long memoryUsedBytes) {

        public GpuStats {
            Validation.requireAtLeast(deviceNumber, "deviceNumber", 0);
            Validation.requireAtLeast(loadPercentage, "loadPercentage", 0);
            Validation.requireAtLeast(memoryTotalBytes, "memoryTotalBytes", 0L);
            Validation.requireAtLeast(memoryUsedBytes, "memoryUsedBytes", 0L);
        }

    }

}
