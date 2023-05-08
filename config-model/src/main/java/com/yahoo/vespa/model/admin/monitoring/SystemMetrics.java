// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import ai.vespa.metrics.HostedNodeAdminMetrics;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * @author gjoranv
 */
// TODO: Move to hosted repo.

public class SystemMetrics {

    public static final MetricSet systemMetricSet = createSystemMetricSet();

    private static MetricSet createSystemMetricSet() {
        Set<Metric> dockerNodeMetrics =
                ImmutableSet.of(new Metric(HostedNodeAdminMetrics.CPU_UTIL.baseName()),
                                new Metric(HostedNodeAdminMetrics.CPU_SYS_UTIL.baseName()),
                                new Metric(HostedNodeAdminMetrics.CPU_THROTTLED_TIME.baseName()),
                                new Metric(HostedNodeAdminMetrics.CPU_THROTTLED_CPU_TIME.baseName()),
                                new Metric(HostedNodeAdminMetrics.CPU_VCPUS.baseName()),
                                new Metric(HostedNodeAdminMetrics.DISK_LIMIT.baseName()),
                                new Metric(HostedNodeAdminMetrics.DISK_USED.baseName()),
                                new Metric(HostedNodeAdminMetrics.DISK_UTIL.baseName()),
                                new Metric(HostedNodeAdminMetrics.MEM_LIMIT.baseName()),
                                new Metric(HostedNodeAdminMetrics.MEM_USED.baseName()),
                                new Metric(HostedNodeAdminMetrics.MEM_UTIL.baseName()),
                                new Metric(HostedNodeAdminMetrics.MEM_TOTAL_USED.baseName()),
                                new Metric(HostedNodeAdminMetrics.MEM_TOTAL_UTIL.baseName()),
                                new Metric(HostedNodeAdminMetrics.GPU_UTIL.baseName()),
                                new Metric(HostedNodeAdminMetrics.GPU_MEM_USED.baseName()),
                                new Metric(HostedNodeAdminMetrics.GPU_MEM_TOTAL.baseName())
                );

        Set<Metric> nonDockerNodeMetrics =
                // Disk metrics should be based on /home, or else '/' - or simply add filesystem as dimension
                ImmutableSet.of(new Metric("cpu.busy.pct", HostedNodeAdminMetrics.CPU_UTIL.baseName()),
                                new Metric("mem.used.pct", HostedNodeAdminMetrics.MEM_UTIL.baseName()),
                                new Metric("mem.active.kb", HostedNodeAdminMetrics.MEM_USED.baseName()),
                                new Metric("mem.total.kb", HostedNodeAdminMetrics.MEM_LIMIT.baseName()),
                                new Metric("used.kb", HostedNodeAdminMetrics.DISK_USED.baseName())
                );

        Set<Metric> systemMetrics = ImmutableSet.<Metric>builder()
                .addAll(dockerNodeMetrics)
                .addAll(nonDockerNodeMetrics)
                .build();

        return new MetricSet("system", systemMetrics);
    }

}
