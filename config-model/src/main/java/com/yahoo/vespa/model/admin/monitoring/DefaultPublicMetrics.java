/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.monitoring;


import com.google.common.collect.ImmutableSet;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.emptyList;

/**
 * @author gjoranv
 */
public class DefaultPublicMetrics {

    public static MetricSet defaultPublicMetricSet = createMetricSet();

    private static MetricSet createMetricSet() {
        return new MetricSet("default-public",
                             getAllMetrics(),
                             emptyList());
    }

    private static Set<Metric> getAllMetrics() {
        return ImmutableSet.<Metric>builder()
                .addAll(getContainerMetrics())
                .addAll(getQrserverMetrics())
                .build();
    }

    private static Set<Metric> getContainerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("http.status.1xx.rate"));
        metrics.add(new Metric("http.status.2xx.rate"));
        metrics.add(new Metric("http.status.3xx.rate"));
        metrics.add(new Metric("http.status.4xx.rate"));
        metrics.add(new Metric("http.status.5xx.rate"));
        metrics.add(new Metric("jdisc.gc.ms.average"));
        metrics.add(new Metric("mem.heap.free.average"));

        return metrics;
    }

    private static Set<Metric> getQrserverMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("queries.rate"));
        metrics.add(new Metric("query_latency.average")); // TODO: Remove in Vespa 8?
        metrics.add(new Metric("query_latency.95percentile"));
        metrics.add(new Metric("query_latency.99percentile"));
        metrics.add(new Metric("hits_per_query.average")); // TODO: Remove in Vespa 8?
        metrics.add(new Metric("totalhits_per_query.average")); // TODO: Remove in Vespa 8?
        metrics.add(new Metric("degraded_queries.rate"));
        metrics.add(new Metric("failed_queries.rate"));
        metrics.add(new Metric("serverActiveThreads.average")); // TODO: Remove in Vespa 8?

        return metrics;
    }

    private DefaultPublicMetrics() { }

}
