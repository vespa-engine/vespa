// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
        metrics.add("mem.util");
        metrics.add("disk.util");
        metrics.add("application_generation");
        metrics.add("in_service");

        metrics.add("queries.rate"); // container
        metrics.add("content.proton.documentdb.matching.queries.rate"); // content

        metrics.add("feed.http-requests.rate"); // container
        metrics.add("vds.filestor.alldisks.allthreads.put.sum.count.rate"); // content
        metrics.add("vds.filestor.alldisks.allthreads.remove.sum.count.rate"); // content
        metrics.add("vds.filestor.alldisks.allthreads.update.sum.count.rate"); // content
        return new MetricSet("autoscaling", toMetrics(metrics));
    }

    private static Set<Metric> toMetrics(List<String> metrics) {
        return metrics.stream().map(Metric::new).collect(Collectors.toSet());
    }

}
