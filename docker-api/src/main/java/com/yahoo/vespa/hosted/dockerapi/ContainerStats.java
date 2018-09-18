// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Wrapper class for {@link com.github.dockerjava.api.model.Statistics} to prevent leaking from docker-java library.
 *
 * @author freva
 */
public class ContainerStats {
    private final Map<String, Object> networks;
    private final Map<String, Object> cpuStats;
    private final Map<String, Object> memoryStats;
    private final Map<String, Object> blkioStats;

    public ContainerStats(Map<String, Object> networks, Map<String, Object> cpuStats,
                          Map<String, Object> memoryStats, Map<String, Object> blkioStats) {
        // Network stats are null when container uses host network
        this.networks = Optional.ofNullable(networks).orElse(Collections.emptyMap());
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
