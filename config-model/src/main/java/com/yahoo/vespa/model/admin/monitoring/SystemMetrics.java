// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * @author gjoranv
 */
public class SystemMetrics {

    public static final String CPU_UTIL = "cpu.util";
    public static final String CPU_SYS_UTIL = "cpu.sys.util";
    public static final String CPU_THROTTLED_TIME = "cpu.throttled_time.rate";
    public static final String CPU_THROTTLED_CPU_TIME = "cpu.throttled_cpu_time.rate";
    public static final String CPU_VCPUS = "cpu.vcpus";
    public static final String DISK_LIMIT = "disk.limit";
    public static final String DISK_USED = "disk.used";
    public static final String DISK_UTIL = "disk.util";
    public static final String MEM_LIMIT = "mem.limit";
    public static final String MEM_USED = "mem.used";
    public static final String MEM_UTIL = "mem.util";
    public static final String MEM_TOTAL_USED = "mem_total.used";
    public static final String MEM_TOTAL_UTIL = "mem_total.util";
    public static final String BANDWIDTH_LIMIT = "bandwidth.limit";
    public static final String GPU_UTIL = "gpu.util";
    public static final String GPU_MEM_USED = "gpu.memory.used";
    public static final String GPU_MEM_TOTAL = "gpu.memory.total";

    public static final MetricSet systemMetricSet = createSystemMetricSet();

    private static MetricSet createSystemMetricSet() {
        Set<Metric> dockerNodeMetrics =
                ImmutableSet.of(new Metric(CPU_UTIL),
                                new Metric(CPU_SYS_UTIL),
                                new Metric(CPU_THROTTLED_TIME),
                                new Metric(CPU_THROTTLED_CPU_TIME),
                                new Metric(CPU_VCPUS),
                                new Metric(DISK_LIMIT),
                                new Metric(DISK_USED),
                                new Metric(DISK_UTIL),
                                new Metric(MEM_LIMIT),
                                new Metric(MEM_USED),
                                new Metric(MEM_UTIL),
                                new Metric(MEM_TOTAL_USED),
                                new Metric(MEM_TOTAL_UTIL),
                                new Metric(BANDWIDTH_LIMIT),
                                new Metric(GPU_UTIL),
                                new Metric(GPU_MEM_USED),
                                new Metric(GPU_MEM_TOTAL)
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
