// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.monitoring;


import com.google.common.collect.ImmutableSet;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static java.util.Collections.singleton;

/**
 * Metrics for the 'default' consumer, which is used by default for the generic metrics api and
 * other user facing apis, e.g. 'prometheus/'.
 *
 * @author gjoranv
 */
public class DefaultMetrics {

    public static final String defaultMetricSetId = "default";

    public static final MetricSet defaultMetricSet = createMetricSet();

    private static MetricSet createMetricSet() {
        return new MetricSet(defaultMetricSetId,
                             getAllMetrics(),
                             singleton(defaultVespaMetricSet));
    }

    private static Set<Metric> getAllMetrics() {
        return ImmutableSet.<Metric>builder()
                .addAll(getContentMetrics())
                .addAll(getContainerMetrics())
                .addAll(getSearchChainMetrics())
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

    private static Set<Metric> getSearchChainMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("queries.rate"));
        metrics.add(new Metric("query_latency.sum"));
        metrics.add(new Metric("query_latency.count"));
        metrics.add(new Metric("query_latency.max"));
        metrics.add(new Metric("query_latency.average")); // TODO: Remove with Vespa 9
        metrics.add(new Metric("query_latency.95percentile"));
        metrics.add(new Metric("query_latency.99percentile"));
        metrics.add(new Metric("hits_per_query.sum"));
        metrics.add(new Metric("hits_per_query.count"));
        metrics.add(new Metric("hits_per_query.max"));
        metrics.add(new Metric("hits_per_query.average")); // TODO: Remove with Vespa 9
        metrics.add(new Metric("totalhits_per_query.sum"));
        metrics.add(new Metric("totalhits_per_query.count"));
        metrics.add(new Metric("totalhits_per_query.max"));
        metrics.add(new Metric("totalhits_per_query.average")); // TODO: Remove with Vespa 9
        metrics.add(new Metric("degraded_queries.rate"));
        metrics.add(new Metric("failed_queries.rate"));
        metrics.add(new Metric("serverActiveThreads.average"));

        return metrics;
    }

    private static Set<Metric> getContentMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("content.proton.search_protocol.docsum.requested_documents.rate"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.latency.sum"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.latency.count"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.latency.max"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.latency.average")); // TODO: Remove with Vespa 9
        metrics.add(new Metric("content.proton.search_protocol.query.latency.sum"));
        metrics.add(new Metric("content.proton.search_protocol.query.latency.count"));
        metrics.add(new Metric("content.proton.search_protocol.query.latency.max"));
        metrics.add(new Metric("content.proton.search_protocol.query.latency.average")); // TODO: Remove with Vespa 9

        metrics.add(new Metric("content.proton.documentdb.documents.total.last"));
        metrics.add(new Metric("content.proton.documentdb.documents.ready.last"));
        metrics.add(new Metric("content.proton.documentdb.documents.active.last"));
        metrics.add(new Metric("content.proton.documentdb.disk_usage.last"));
        metrics.add(new Metric("content.proton.documentdb.memory_usage.allocated_bytes.last"));

        metrics.add(new Metric("content.proton.resource_usage.disk.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory.average"));

        metrics.add(new Metric("content.proton.documentdb.matching.docs_matched.rate"));
        metrics.add(new Metric("content.proton.documentdb.matching.docs_reranked.rate"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.average")); // TODO: Remove with Vespa 9
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_latency.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_latency.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_latency.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_latency.average")); // TODO: Remove with Vespa 9
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.average")); // TODO: Remove with Vespa 9

        metrics.add(new Metric("content.proton.transactionlog.disk_usage.last"));

        return metrics;
    }

    private DefaultMetrics() { }

}
