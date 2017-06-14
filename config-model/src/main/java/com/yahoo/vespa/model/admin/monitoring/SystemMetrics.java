// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * @author gjoranv
 */
@SuppressWarnings("UnusedDeclaration") // Used by model amenders
public class SystemMetrics {
    public static final String CPU_UTIL = "cpu.util";
    public static final String DISK_LIMIT = "disk.limit";
    public static final String DISK_USED = "disk.used";
    public static final String DISK_UTIL = "disk.util";
    public static final String MEM_LIMIT = "mem.limit";
    public static final String MEM_USED = "mem.used";
    public static final String MEM_UTIL = "mem.util";

    public static final MetricSet systemMetricSet = createSystemMetricSet();

    private static MetricSet createSystemMetricSet() {
        Set<Metric> dockerNodeMetrics =
                ImmutableSet.of(new Metric(CPU_UTIL),
                                new Metric(DISK_LIMIT),
                                new Metric(DISK_USED),
                                new Metric(DISK_UTIL),
                                new Metric(MEM_LIMIT),
                                new Metric(MEM_USED),
                                new Metric(MEM_UTIL)
                );

        Set<Metric> nonDockerNodeMetrics =
                // Disk metrics should be based on /home, or else '/' - or simply add filesystem as dimension
                ImmutableSet.of(new Metric("cpu.busy.pct", CPU_UTIL),
                                new Metric("mem.used.pct", MEM_UTIL),
                                new Metric("mem.active.kb", MEM_USED),
                                new Metric("mem.total.kb", MEM_LIMIT),
                                new Metric("used.kb", DISK_USED)
                );

        Set<Metric> systemMetrics = ImmutableSet.<Metric>builder()
                .addAll(dockerNodeMetrics)
                .addAll(nonDockerNodeMetrics)
                .build();

        return new MetricSet("system", systemMetrics);
    }
}
