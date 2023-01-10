// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.monitoring;

import com.yahoo.metrics.ContainerMetrics;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;

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
                             Set.of(defaultVespaMetricSet));
    }

    private static Set<Metric> getAllMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addContentMetrics(metrics);
        addContainerMetrics(metrics);
        addSearchChainMetrics(metrics);
        return Collections.unmodifiableSet(metrics);
    }

    private static void addContainerMetrics(Set<Metric> metrics) {
        metrics.add(new Metric(ContainerMetrics.HTTP_STATUS_1XX.rate()));
        metrics.add(new Metric(ContainerMetrics.HTTP_STATUS_2XX.rate()));
        metrics.add(new Metric(ContainerMetrics.HTTP_STATUS_3XX.rate()));
        metrics.add(new Metric(ContainerMetrics.HTTP_STATUS_4XX.rate()));
        metrics.add(new Metric(ContainerMetrics.HTTP_STATUS_5XX.rate()));
        metrics.add(new Metric(ContainerMetrics.JDISC_GC_MS.average()));
        metrics.add(new Metric("mem.heap.free.average"));
    }

    private static void addSearchChainMetrics(Set<Metric> metrics) {
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
    }

    private static void addContentMetrics(Set<Metric> metrics) {
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
    }

    private DefaultMetrics() { }

}
