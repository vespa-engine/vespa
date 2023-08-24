// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics.set;

import ai.vespa.metrics.HostedNodeAdminMetrics;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author gjoranv
 */
// TODO: Move to hosted repo.

public class SystemMetrics {

    public static final MetricSet systemMetricSet = createSystemMetricSet();

    private static MetricSet createSystemMetricSet() {
        Set<Metric> dockerNodeMetrics =
                Set.of(new Metric(HostedNodeAdminMetrics.CPU_UTIL.baseName()),
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
                                new Metric(HostedNodeAdminMetrics.MEM_SOCK.baseName()),
                                new Metric(HostedNodeAdminMetrics.MEM_SLAB_RECLAIMABLE.baseName()),
                                new Metric(HostedNodeAdminMetrics.MEM_SLAB.baseName()),
                                new Metric(HostedNodeAdminMetrics.MEM_ANON.baseName()),
                                new Metric(HostedNodeAdminMetrics.GPU_UTIL.baseName()),
                                new Metric(HostedNodeAdminMetrics.GPU_MEM_USED.baseName()),
                                new Metric(HostedNodeAdminMetrics.GPU_MEM_TOTAL.baseName())
                );

        Set<Metric> nonDockerNodeMetrics =
                // Disk metrics should be based on /home, or else '/' - or simply add filesystem as dimension
                Set.of(new Metric("cpu.busy.pct", HostedNodeAdminMetrics.CPU_UTIL.baseName()),
                                new Metric("mem.used.pct", HostedNodeAdminMetrics.MEM_UTIL.baseName()),
                                new Metric("mem.active.kb", HostedNodeAdminMetrics.MEM_USED.baseName()),
                                new Metric("mem.total.kb", HostedNodeAdminMetrics.MEM_LIMIT.baseName()),
                                new Metric("used.kb", HostedNodeAdminMetrics.DISK_USED.baseName())
                );


        Set<Metric> metrics = new LinkedHashSet<>();
        metrics.addAll(dockerNodeMetrics);
        metrics.addAll(nonDockerNodeMetrics);

        return new MetricSet("system", metrics);
    }

}
