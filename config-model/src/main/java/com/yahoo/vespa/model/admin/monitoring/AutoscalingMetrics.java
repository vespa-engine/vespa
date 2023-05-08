// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import ai.vespa.metrics.ContainerMetrics;
import ai.vespa.metrics.HostedNodeAdminMetrics;
import ai.vespa.metrics.SearchNodeMetrics;
import ai.vespa.metrics.StorageMetrics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Metrics used for autoscaling.
 * See com.yahoo.vespa.hosted.provision.autoscale.MetricsResponse
 *
 * @author bratseth
 */
public class AutoscalingMetrics {

    public static final MetricSet autoscalingMetricSet = create();

    private static MetricSet create() {
        List<String> metrics = new ArrayList<>();

        metrics.add(HostedNodeAdminMetrics.CPU_UTIL.baseName());
        metrics.add(HostedNodeAdminMetrics.GPU_UTIL.baseName());

        // Memory util
        metrics.add(HostedNodeAdminMetrics.MEM_UTIL.baseName()); // node level - default
        metrics.add(HostedNodeAdminMetrics.GPU_MEM_USED.baseName());
        metrics.add(HostedNodeAdminMetrics.GPU_MEM_TOTAL.baseName());
        metrics.add(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY.average()); // the basis for blocking

        // Disk util
        metrics.add(HostedNodeAdminMetrics.DISK_UTIL.baseName()); // node level -default
        metrics.add(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK.average()); // the basis for blocking

        metrics.add(ContainerMetrics.APPLICATION_GENERATION.last());
        metrics.add(SearchNodeMetrics.CONTENT_PROTON_CONFIG_GENERATION.last());

        metrics.add(ContainerMetrics.IN_SERVICE.last());

        // Query rate
        metrics.add(ContainerMetrics.QUERIES.rate());
        metrics.add(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERIES.rate());

        // Write rate
        metrics.add(ContainerMetrics.FEED_HTTP_REQUESTS.rate());
        metrics.add(StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_COUNT.rate());
        metrics.add(StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_COUNT.rate());
        metrics.add(StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_COUNT.rate());

        return new MetricSet("autoscaling", toMetrics(metrics));
    }

    private static Set<Metric> toMetrics(List<String> metrics) {
        return metrics.stream().map(Metric::new).collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

}
