/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.monitoring;


import com.google.common.collect.ImmutableSet;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.emptyList;

/**
 * TODO: Add content metrics.
 *
 * @author gjoranv
 */
public class DefaultPublicMetrics {

    public static MetricSet defaultPublicMetricSet = createMetricSet();

    private static MetricSet createMetricSet() {
        return new MetricSet("public",
                             getAllMetrics(),
                             emptyList());
    }

    private static Set<Metric> getAllMetrics() {
        return ImmutableSet.<Metric>builder()
                .addAll(getContentMetrics())
                .addAll(getStorageMetrics())
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
        metrics.add(new Metric("query_latency.average"));
        metrics.add(new Metric("query_latency.95percentile"));
        metrics.add(new Metric("query_latency.99percentile"));
        metrics.add(new Metric("hits_per_query.average"));
        metrics.add(new Metric("totalhits_per_query.average"));
        metrics.add(new Metric("degraded_queries.rate"));
        metrics.add(new Metric("failed_queries.rate"));
        metrics.add(new Metric("serverActiveThreads.average"));

        return metrics;
    }

    private static Set<Metric> getContentMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("content.proton.docsum.docs.rate"));
        metrics.add(new Metric("content.proton.docsum.latency.average"));

        metrics.add(new Metric("content.proton.transport.query.count.rate"));
        metrics.add(new Metric("content.proton.transport.query.latency.average"));

        metrics.add(new Metric("content.proton.documentdb.documents.total.last"));
        metrics.add(new Metric("content.proton.documentdb.documents.ready.last"));
        metrics.add(new Metric("content.proton.documentdb.documents.active.last"));
        metrics.add(new Metric("content.proton.documentdb.index.docs_in_memory.last"));
        metrics.add(new Metric("content.proton.documentdb.disk_usage.last"));

        metrics.add(new Metric("content.proton.documentdb.job.total.average"));
        metrics.add(new Metric("content.proton.documentdb.job.attribute_flush.average"));
        metrics.add(new Metric("content.proton.documentdb.job.disk_index_fusion.average"));
        metrics.add(new Metric("content.proton.documentdb.job.document_store_compact.average"));
        metrics.add(new Metric("content.proton.documentdb.job.memory_index_flush.average"));

        metrics.add(new Metric("content.proton.resource_usage.disk.average"));
        metrics.add(new Metric("content.proton.resource_usage.disk_utilization.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory.average"));

        metrics.add(new Metric("content.proton.documentdb.ready.document_store.memory_usage.allocated_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.cache.hit_rate.average"));
        metrics.add(new Metric("content.proton.documentdb.index.memory_usage.allocated_bytes.average"));

        metrics.add(new Metric("content.proton.documentdb.matching.docs_matched.rate"));
        metrics.add(new Metric("content.proton.documentdb.matching.docs_reranked.rate"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_latency.average"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.average"));

        return metrics;
    }

    private static Set<Metric> getStorageMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.visit.sum.count.rate"));

        return metrics;
    }

    private DefaultPublicMetrics() { }

}
