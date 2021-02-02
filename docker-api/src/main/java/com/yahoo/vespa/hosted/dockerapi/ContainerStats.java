// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CPU, memory and network statistics collected from a container.
 *
 * @author freva
 */
// TODO: Move this to node-admin when docker-api module can be removed
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

    /** Statistics for network usage */
    public static class NetworkStats {

        private final long rxBytes;
        private final long rxDropped;
        private final long rxErrors;
        private final long txBytes;
        private final long txDropped;
        private final long txErrors;

        public NetworkStats(long rxBytes, long rxDropped, long rxErrors, long txBytes, long txDropped, long txErrors) {
            this.rxBytes = rxBytes;
            this.rxDropped = rxDropped;
            this.rxErrors = rxErrors;
            this.txBytes = txBytes;
            this.txDropped = txDropped;
            this.txErrors = txErrors;
        }

        /** Returns received bytes */
        public long getRxBytes() { return this.rxBytes; }

        /** Returns received bytes, which was dropped */
        public long getRxDropped() { return this.rxDropped; }

        /** Returns received errors */
        public long getRxErrors() { return this.rxErrors; }

        /** Returns transmitted bytes */
        public long getTxBytes() { return this.txBytes; }

        /** Returns transmitted bytes, which was dropped */
        public long getTxDropped() { return this.txDropped; }

        /** Returns transmission errors */
        public long getTxErrors() { return this.txErrors; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NetworkStats that = (NetworkStats) o;
            return rxBytes == that.rxBytes && rxDropped == that.rxDropped && rxErrors == that.rxErrors && txBytes == that.txBytes && txDropped == that.txDropped && txErrors == that.txErrors;
        }

        @Override
        public int hashCode() {
            return Objects.hash(rxBytes, rxDropped, rxErrors, txBytes, txDropped, txErrors);
        }

        @Override
        public String toString() {
            return "NetworkStats{" +
                   "rxBytes=" + rxBytes +
                   ", rxDropped=" + rxDropped +
                   ", rxErrors=" + rxErrors +
                   ", txBytes=" + txBytes +
                   ", txDropped=" + txDropped +
                   ", txErrors=" + txErrors +
                   '}';
        }

    }

    /** Statistics for memory usage */
    public static class MemoryStats {

        private final long cache;
        private final long usage;
        private final long limit;

        public MemoryStats(long cache, long usage, long limit) {
            this.cache = cache;
            this.usage = usage;
            this.limit = limit;
        }

        /** Returns memory used by cache in bytes */
        public long getCache() { return this.cache; }

        /** Returns memory usage in bytes */
        public long getUsage() { return this.usage; }

        /** Returns memory limit in bytes */
        public long getLimit() { return this.limit; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MemoryStats that = (MemoryStats) o;
            return cache == that.cache && usage == that.usage && limit == that.limit;
        }

        @Override
        public int hashCode() {
            return Objects.hash(cache, usage, limit);
        }

        @Override
        public String toString() {
            return "MemoryStats{" +
                   "cache=" + cache +
                   ", usage=" + usage +
                   ", limit=" + limit +
                   '}';
        }

    }

    /** Statistics for CPU usage */
    public static class CpuStats {

        private final int onlineCpus;
        private final long systemCpuUsage;
        private final long totalUsage;
        private final long usageInKernelMode;
        private final long throttledTime;
        private final long throttlingActivePeriods;
        private final long throttledPeriods;

        public CpuStats(int onlineCpus, long systemCpuUsage, long totalUsage, long usageInKernelMode,
                        long throttledTime, long throttlingActivePeriods, long throttledPeriods) {
            this.onlineCpus = onlineCpus;
            this.systemCpuUsage = systemCpuUsage;
            this.totalUsage = totalUsage;
            this.usageInKernelMode = usageInKernelMode;
            this.throttledTime = throttledTime;
            this.throttlingActivePeriods = throttlingActivePeriods;
            this.throttledPeriods = throttledPeriods;
        }

        public int getOnlineCpus() { return this.onlineCpus; }

        /** Total CPU time (in ns) spent executing all the processes on this host */
        public long getSystemCpuUsage() { return this.systemCpuUsage; }

        /** Total CPU time (in ns) spent running all the processes in this container */
        public long getTotalUsage() { return totalUsage; }

        /** Total CPU time (in ns) spent in kernel mode while executing processes in this container */
        public long getUsageInKernelMode() { return usageInKernelMode; }

        /** Total CPU time (in ns) processes in this container were throttled for */
        public long getThrottledTime() { return throttledTime; }

        /** Number of periods with throttling enabled for this container */
        public long getThrottlingActivePeriods() { return throttlingActivePeriods; }

        /** Number of periods this container hit the throttling limit */
        public long getThrottledPeriods() { return throttledPeriods; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CpuStats cpuStats = (CpuStats) o;
            return onlineCpus == cpuStats.onlineCpus && systemCpuUsage == cpuStats.systemCpuUsage && totalUsage == cpuStats.totalUsage && usageInKernelMode == cpuStats.usageInKernelMode && throttledTime == cpuStats.throttledTime && throttlingActivePeriods == cpuStats.throttlingActivePeriods && throttledPeriods == cpuStats.throttledPeriods;
        }

        @Override
        public int hashCode() {
            return Objects.hash(onlineCpus, systemCpuUsage, totalUsage, usageInKernelMode, throttledTime, throttlingActivePeriods, throttledPeriods);
        }

        @Override
        public String toString() {
            return "CpuStats{" +
                   "onlineCpus=" + onlineCpus +
                   ", systemCpuUsage=" + systemCpuUsage +
                   ", totalUsage=" + totalUsage +
                   ", usageInKernelMode=" + usageInKernelMode +
                   ", throttledTime=" + throttledTime +
                   ", throttlingActivePeriods=" + throttlingActivePeriods +
                   ", throttledPeriods=" + throttledPeriods +
                   '}';
        }

    }

}
