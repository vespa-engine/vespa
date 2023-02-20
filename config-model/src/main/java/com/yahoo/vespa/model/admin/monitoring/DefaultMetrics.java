// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.admin.monitoring;

import com.yahoo.metrics.ContainerMetrics;
import com.yahoo.metrics.SearchNodeMetrics;

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
        metrics.add(new Metric(ContainerMetrics.MEM_HEAP_FREE.average()));
    }

    private static void addSearchChainMetrics(Set<Metric> metrics) {
        metrics.add(new Metric(ContainerMetrics.QUERIES.rate()));
        metrics.add(new Metric(ContainerMetrics.QUERY_LATENCY.sum()));
        metrics.add(new Metric(ContainerMetrics.QUERY_LATENCY.count()));
        metrics.add(new Metric(ContainerMetrics.QUERY_LATENCY.max()));
        metrics.add(new Metric(ContainerMetrics.QUERY_LATENCY.ninety_five_percentile()));
        metrics.add(new Metric(ContainerMetrics.QUERY_LATENCY.ninety_nine_percentile()));
        metrics.add(new Metric(ContainerMetrics.HITS_PER_QUERY.sum()));
        metrics.add(new Metric(ContainerMetrics.HITS_PER_QUERY.count()));
        metrics.add(new Metric(ContainerMetrics.HITS_PER_QUERY.max()));
        metrics.add(new Metric(ContainerMetrics.TOTAL_HITS_PER_QUERY.sum()));
        metrics.add(new Metric(ContainerMetrics.TOTAL_HITS_PER_QUERY.count()));
        metrics.add(new Metric(ContainerMetrics.TOTAL_HITS_PER_QUERY.max()));
        metrics.add(new Metric(ContainerMetrics.DEGRADED_QUERIES.rate()));
        metrics.add(new Metric(ContainerMetrics.FAILED_QUERIES.rate()));
        metrics.add(new Metric(ContainerMetrics.QUERY_LATENCY.average())); // TODO: Remove with Vespa 9
        metrics.add(new Metric(ContainerMetrics.HITS_PER_QUERY.average())); // TODO: Remove with Vespa 9
        metrics.add(new Metric(ContainerMetrics.TOTAL_HITS_PER_QUERY.average())); // TODO: Remove with Vespa 9
        metrics.add(new Metric(ContainerMetrics.SERVER_ACTIVE_THREADS.average())); // TODO: Remove on Vespa 9. Use jdisc.thread_pool.active_threads.
    }

    private static void addContentMetrics(Set<Metric> metrics) {
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REQUESTED_DOCUMENTS.rate()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_LATENCY.sum()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_LATENCY.count()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_LATENCY.max()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_LATENCY.average())); // TODO: Remove with Vespa 9
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_LATENCY.sum()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_LATENCY.count()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_LATENCY.max()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_LATENCY.average())); // TODO: Remove with Vespa 9

        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_TOTAL.last()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_READY.last()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_ACTIVE.last()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DISK_USAGE.last()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MEMORY_USAGE_ALLOCATED_BYTES.last()));

        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK.average()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY.average()));

        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_MATCHED.rate()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_RERANKED.rate()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_SETUP_TIME.sum()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_SETUP_TIME.count()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_SETUP_TIME.max()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_SETUP_TIME.average())); // TODO: Remove with Vespa 9
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_LATENCY.sum()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_LATENCY.count()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_LATENCY.max()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_LATENCY.average())); // TODO: Remove with Vespa 9
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_RERANK_TIME.sum()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_RERANK_TIME.count()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_RERANK_TIME.max()));
        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_RERANK_TIME.average())); // TODO: Remove with Vespa 9

        metrics.add(new Metric(SearchNodeMetrics.CONTENT_PROTON_TRANSACTIONLOG_DISK_USAGE.last()));
    }

    private DefaultMetrics() { }

}
