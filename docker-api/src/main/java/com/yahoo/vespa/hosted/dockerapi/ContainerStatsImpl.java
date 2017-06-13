// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Map;

/**
 * Wrapper class for {@link com.github.dockerjava.api.model.Statistics} to prevent leaking from docker-java library.
 *
 * @author valerijf
 */
public class ContainerStatsImpl implements Docker.ContainerStats {
    private final Map<String, Object> networks;
    private final Map<String, Object> cpuStats;
    private final Map<String, Object> memoryStats;
    private final Map<String, Object> blkioStats;

    public ContainerStatsImpl(Map<String, Object> networks, Map<String, Object> cpuStats,
                              Map<String, Object> memoryStats, Map<String, Object> blkioStats) {
        this.networks = networks;
        this.cpuStats = cpuStats;
        this.memoryStats = memoryStats;
        this.blkioStats = blkioStats;
    }

    public Map<String, Object> getNetworks() {
        return networks;
    }

    public Map<String, Object> getCpuStats() {
        return cpuStats;
    }

    public Map<String, Object> getMemoryStats() {
        return memoryStats;
    }

    public Map<String, Object> getBlkioStats() {
        return blkioStats;
    }
}
