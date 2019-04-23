// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.CpuStatsConfig;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import com.github.dockerjava.api.model.Statistics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Wrapper class for {@link com.github.dockerjava.api.model.Statistics} to prevent leaking from docker-java library.
 *
 * @author freva
 */
public class ContainerStats {
    private final Map<String, NetworkStats> networkStatsByInterface;
    private final MemoryStats memoryStats;
    private final CpuStats cpuStats;

    ContainerStats(Statistics statistics) {
        // Network stats are null when container uses host network
        this.networkStatsByInterface = Optional.ofNullable(statistics.getNetworks()).orElseGet(Map::of)
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new NetworkStats(e.getValue()),
                        (u, v) -> { throw new IllegalStateException(); },
                        TreeMap::new));
        this.memoryStats = new MemoryStats(statistics.getMemoryStats());
        this.cpuStats = new CpuStats(statistics.getCpuStats());
    }

    public Map<String, NetworkStats> getNetworks() {
        return networkStatsByInterface;
    }

    public MemoryStats getMemoryStats() {
        return memoryStats;
    }

    public CpuStats getCpuStats() {
        return cpuStats;
    }

    public static class NetworkStats {
        private final long rxBytes;
        private final long rxDropped;
        private final long rxErrors;
        private final long txBytes;
        private final long txDropped;
        private final long txErrors;

        private NetworkStats(StatisticNetworksConfig statisticNetworksConfig) {
            this.rxBytes = statisticNetworksConfig.getRxBytes();
            this.rxDropped = statisticNetworksConfig.getRxDropped();
            this.rxErrors = statisticNetworksConfig.getRxErrors();
            this.txBytes = statisticNetworksConfig.getTxBytes();
            this.txDropped = statisticNetworksConfig.getTxDropped();
            this.txErrors = statisticNetworksConfig.getTxErrors();
        }

        public long getRxBytes() { return this.rxBytes; }
        public long getRxDropped() { return this.rxDropped; }
        public long getRxErrors() { return this.rxErrors; }
        public long getTxBytes() { return this.txBytes; }
        public long getTxDropped() { return this.txDropped; }
        public long getTxErrors() { return this.txErrors; }
    }

    public class MemoryStats {
        private final long cache;
        private final long usage;
        private final long limit;

        private MemoryStats(MemoryStatsConfig memoryStats) {
            this.cache = memoryStats.getStats().getCache();
            this.usage = memoryStats.getUsage();
            this.limit = memoryStats.getLimit();
        }

        public long getCache() { return this.cache; }
        public long getUsage() { return this.usage; }
        public long getLimit() { return this.limit; }
    }

    public class CpuStats {
        private final int onlineCpus;
        private final long systemCpuUsage;
        private final long totalUsage;
        private final long usageInKernelMode;

        public CpuStats(CpuStatsConfig cpuStats) {
            // Added in 1.27
            this.onlineCpus = cpuStats.getCpuUsage().getPercpuUsage().size();
            this.systemCpuUsage = cpuStats.getSystemCpuUsage();
            this.totalUsage = cpuStats.getCpuUsage().getTotalUsage();
            this.usageInKernelMode = cpuStats.getCpuUsage().getUsageInKernelmode();
        }

        public int getOnlineCpus() { return this.onlineCpus; }
        public long getSystemCpuUsage() { return this.systemCpuUsage; }
        public long getTotalUsage() { return totalUsage; }
        public long getUsageInKernelMode() { return usageInKernelMode; }
    }

    // For testing only, create ContainerStats from JSON returned by docker daemon stats API
    public static ContainerStats fromJson(String json) {
        try {
            Statistics statistics = new ObjectMapper().readValue(json, Statistics.class);
            return new ContainerStats(statistics);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
