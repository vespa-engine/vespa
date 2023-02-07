// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;
import com.yahoo.metrics.ContainerMetrics;
import com.yahoo.metrics.SearchNodeMetrics;
import com.yahoo.metrics.StorageMetrics;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Metrics used for autoscaling
 *
 * @author bratseth
 */
public class AutoscalingMetrics {

    public static final MetricSet autoscalingMetricSet = create();

    private static MetricSet create() {
        List<String> metrics = new ArrayList<>();

        metrics.add("cpu.util");

        // Memory util
        metrics.add("mem.util"); // node level - default
        metrics.add(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY.average()); // better for content as it is the basis for blocking

        // Disk util
        metrics.add("disk.util"); // node level -default
        metrics.add(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK.average()); // better for content as it is the basis for blocking

        metrics.add("application_generation");

        metrics.add("in_service");

        // Query rate
        metrics.add(ContainerMetrics.QUERIES.rate()); // container
        metrics.add(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERIES.rate()); // content

        // Write rate
        metrics.add(ContainerMetrics.FEED_HTTP_REQUESTS.rate()); // container
        metrics.add(StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_COUNT.rate()); // content
        metrics.add(StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_COUNT.rate()); // content
        metrics.add(StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_COUNT.rate()); // content

        return new MetricSet("autoscaling", toMetrics(metrics));
    }

    private static Set<Metric> toMetrics(List<String> metrics) {
        return metrics.stream().map(Metric::new).collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

}
