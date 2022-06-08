// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

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
        metrics.add("content.proton.resource_usage.memory.average"); // better for content as it is the basis for blocking

        // Disk util
        metrics.add("disk.util"); // node level -default
        metrics.add("content.proton.resource_usage.disk.average"); // better for content as it is the basis for blocking

        metrics.add("application_generation");

        metrics.add("in_service");

        // Query rate
        metrics.add("queries.rate"); // container
        metrics.add("content.proton.documentdb.matching.queries.rate"); // content

        // Write rate
        metrics.add("feed.http-requests.rate"); // container
        metrics.add("vds.filestor.allthreads.put.count.rate"); // content
        metrics.add("vds.filestor.allthreads.remove.count.rate"); // content
        metrics.add("vds.filestor.allthreads.update.count.rate"); // content

        return new MetricSet("autoscaling", toMetrics(metrics));
    }

    private static Set<Metric> toMetrics(List<String> metrics) {
        return metrics.stream().map(Metric::new).collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    }

}
