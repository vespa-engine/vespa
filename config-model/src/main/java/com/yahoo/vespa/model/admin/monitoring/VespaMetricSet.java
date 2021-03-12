// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static java.util.Collections.singleton;

/**
 * Encapsulates vespa service metrics.
 *
 * @author gjoranv
 */
public class VespaMetricSet {

    public static final MetricSet vespaMetricSet = new MetricSet("vespa",
                                                                 getVespaMetrics(),
                                                                 singleton(defaultVespaMetricSet));

    private static Set<Metric> getVespaMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.addAll(getSearchNodeMetrics());
        metrics.addAll(getStorageMetrics());
        metrics.addAll(getDocprocMetrics());
        metrics.addAll(getClusterControllerMetrics());
        metrics.addAll(getQrserverMetrics());
        metrics.addAll(getContainerMetrics());
        metrics.addAll(getConfigServerMetrics());
        metrics.addAll(getSentinelMetrics());
        metrics.addAll(getOtherMetrics());

        return Collections.unmodifiableSet(metrics);
    }

    private static Set<Metric> getSentinelMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("sentinel.restarts.count"));
        metrics.add(new Metric("sentinel.totalRestarts.last"));
        metrics.add(new Metric("sentinel.uptime.last"));

        metrics.add(new Metric("sentinel.running.count"));
        metrics.add(new Metric("sentinel.running.last"));

        return metrics;
    }

    private static Set<Metric> getOtherMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("slobrok.heartbeats.failed.count"));
        metrics.add(new Metric("logd.processed.lines.count"));

        // Java (JRT) TLS metrics
        metrics.add(new Metric("jrt.transport.tls-certificate-verification-failures"));
        metrics.add(new Metric("jrt.transport.peer-authorization-failures"));
        metrics.add(new Metric("jrt.transport.server.tls-connections-established"));
        metrics.add(new Metric("jrt.transport.client.tls-connections-established"));
        metrics.add(new Metric("jrt.transport.server.unencrypted-connections-established"));
        metrics.add(new Metric("jrt.transport.client.unencrypted-connections-established"));

        // C++ TLS metrics
        metrics.add(new Metric("vds.server.network.tls-handshakes-failed"));
        metrics.add(new Metric("vds.server.network.peer-authorization-failures"));
        metrics.add(new Metric("vds.server.network.client.tls-connections-established"));
        metrics.add(new Metric("vds.server.network.server.tls-connections-established"));
        metrics.add(new Metric("vds.server.network.client.insecure-connections-established"));
        metrics.add(new Metric("vds.server.network.server.insecure-connections-established"));
        metrics.add(new Metric("vds.server.network.tls-connections-broken"));
        metrics.add(new Metric("vds.server.network.failed-tls-config-reloads"));

        // C++ Fnet metrics
        metrics.add(new Metric("vds.server.fnet.num-connections"));

        return metrics;
    }

    private static Set<Metric> getConfigServerMetrics() {
        Set<Metric> metrics =new LinkedHashSet<>();

        metrics.add(new Metric("configserver.requests.count"));
        metrics.add(new Metric("configserver.failedRequests.count"));
        metrics.add(new Metric("configserver.latency.max"));
        metrics.add(new Metric("configserver.latency.sum"));
        metrics.add(new Metric("configserver.latency.count"));
        metrics.add(new Metric("configserver.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("configserver.cacheConfigElems.last"));
        metrics.add(new Metric("configserver.cacheChecksumElems.last"));
        metrics.add(new Metric("configserver.hosts.last"));
        metrics.add(new Metric("configserver.delayedResponses.count"));
        metrics.add(new Metric("configserver.sessionChangeErrors.count"));

        metrics.add(new Metric("configserver.zkZNodes.last"));
        metrics.add(new Metric("configserver.zkAvgLatency.last"));
        metrics.add(new Metric("configserver.zkMaxLatency.last"));
        metrics.add(new Metric("configserver.zkConnections.last"));
        metrics.add(new Metric("configserver.zkOutstandingRequests.last"));

        return metrics;
    }

    private static Set<Metric> getContainerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, "jdisc.http.requests", List.of("rate", "count"));

        metrics.add(new Metric("handled.requests.count"));
        metrics.add(new Metric("handled.latency.max"));
        metrics.add(new Metric("handled.latency.sum"));
        metrics.add(new Metric("handled.latency.count"));
        metrics.add(new Metric("handled.latency.average")); // TODO: Remove in Vespa 8

        metrics.add(new Metric("serverRejectedRequests.rate"));
        metrics.add(new Metric("serverRejectedRequests.count"));

        metrics.add(new Metric("serverThreadPoolSize.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("serverThreadPoolSize.min")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("serverThreadPoolSize.max"));
        metrics.add(new Metric("serverThreadPoolSize.rate")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("serverThreadPoolSize.count")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("serverThreadPoolSize.last"));

        metrics.add(new Metric("serverActiveThreads.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("serverActiveThreads.min"));
        metrics.add(new Metric("serverActiveThreads.max"));
        metrics.add(new Metric("serverActiveThreads.rate")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("serverActiveThreads.sum"));
        metrics.add(new Metric("serverActiveThreads.count"));
        metrics.add(new Metric("serverActiveThreads.last"));

        metrics.add(new Metric("serverNumOpenConnections.average"));
        metrics.add(new Metric("serverNumOpenConnections.max"));
        metrics.add(new Metric("serverNumOpenConnections.last"));
        metrics.add(new Metric("serverNumConnections.average"));
        metrics.add(new Metric("serverNumConnections.max"));
        metrics.add(new Metric("serverNumConnections.last"));

        {
            List<String> suffixes = List.of("sum", "count", "last", "min", "max");
            addMetric(metrics, "jdisc.thread_pool.unhandled_exceptions", suffixes);
            addMetric(metrics, "jdisc.thread_pool.work_queue.capacity", suffixes);
            addMetric(metrics, "jdisc.thread_pool.work_queue.size", suffixes);
        }

        metrics.add(new Metric("httpapi_latency.max"));
        metrics.add(new Metric("httpapi_latency.sum"));
        metrics.add(new Metric("httpapi_latency.count"));
        metrics.add(new Metric("httpapi_latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("httpapi_pending.max"));
        metrics.add(new Metric("httpapi_pending.sum"));
        metrics.add(new Metric("httpapi_pending.count"));
        metrics.add(new Metric("httpapi_pending.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("httpapi_num_operations.rate"));
        metrics.add(new Metric("httpapi_num_updates.rate"));
        metrics.add(new Metric("httpapi_num_removes.rate"));
        metrics.add(new Metric("httpapi_num_puts.rate"));
        metrics.add(new Metric("httpapi_succeeded.rate"));
        metrics.add(new Metric("httpapi_failed.rate"));
        metrics.add(new Metric("httpapi_parse_error.rate"));

        metrics.add(new Metric("mem.heap.total.average"));
        metrics.add(new Metric("mem.heap.free.average"));
        metrics.add(new Metric("mem.heap.used.average"));
        metrics.add(new Metric("mem.heap.used.max"));
        metrics.add(new Metric("jdisc.memory_mappings.max"));
        metrics.add(new Metric("jdisc.open_file_descriptors.max"));

        metrics.add(new Metric("jdisc.gc.count.average"));
        metrics.add(new Metric("jdisc.gc.count.max"));
        metrics.add(new Metric("jdisc.gc.count.last"));
        metrics.add(new Metric("jdisc.gc.ms.average"));
        metrics.add(new Metric("jdisc.gc.ms.max"));
        metrics.add(new Metric("jdisc.gc.ms.last"));

        metrics.add(new Metric("jdisc.deactivated_containers.total.last"));
        metrics.add(new Metric("jdisc.deactivated_containers.with_retained_refs.last"));

        metrics.add(new Metric("athenz-tenant-cert.expiry.seconds.last"));

        metrics.add(new Metric("jdisc.http.request.prematurely_closed.rate"));

        metrics.add(new Metric("http.status.1xx.rate"));
        metrics.add(new Metric("http.status.2xx.rate"));
        metrics.add(new Metric("http.status.3xx.rate"));
        metrics.add(new Metric("http.status.4xx.rate"));
        metrics.add(new Metric("http.status.5xx.rate"));
        metrics.add(new Metric("http.status.401.rate"));
        metrics.add(new Metric("http.status.403.rate"));

        metrics.add(new Metric("jdisc.http.request.uri_length.max"));
        metrics.add(new Metric("jdisc.http.request.uri_length.sum"));
        metrics.add(new Metric("jdisc.http.request.uri_length.count"));
        metrics.add(new Metric("jdisc.http.request.uri_length.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("jdisc.http.request.content_size.max"));
        metrics.add(new Metric("jdisc.http.request.content_size.sum"));
        metrics.add(new Metric("jdisc.http.request.content_size.count"));
        metrics.add(new Metric("jdisc.http.request.content_size.average")); // TODO: Remove in Vespa 8

        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.missing_client_cert.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.expired_client_cert.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.invalid_client_cert.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.incompatible_protocols.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.incompatible_ciphers.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.unknown.rate"));

        metrics.add(new Metric("jdisc.http.handler.unhandled_exceptions.rate"));

        addMetric(metrics, "jdisc.http.jetty.threadpool.thread.max", List.of("last"));
        addMetric(metrics, "jdisc.http.jetty.threadpool.thread.reserved", List.of("last"));
        addMetric(metrics, "jdisc.http.jetty.threadpool.thread.busy", List.of("sum", "count", "min", "max"));
        addMetric(metrics, "jdisc.http.jetty.threadpool.thread.total", List.of("sum", "count", "min", "max"));
        addMetric(metrics, "jdisc.http.jetty.threadpool.queue.size", List.of("sum", "count", "min", "max"));

        addMetric(metrics, "jdisc.http.filtering.request.handled", List.of("rate"));
        addMetric(metrics, "jdisc.http.filtering.request.unhandled", List.of("rate"));
        addMetric(metrics, "jdisc.http.filtering.response.handled", List.of("rate"));
        addMetric(metrics, "jdisc.http.filtering.response.unhandled", List.of("rate"));

        addMetric(metrics, "jdisc.application.failed_component_graphs", List.of("rate"));

        addMetric(metrics, "jdisc.http.filter.rule.blocked_requests", List.of("rate"));
        addMetric(metrics, "jdisc.http.filter.rule.allowed_requests", List.of("rate"));

        return metrics;
    }

    private static Set<Metric> getClusterControllerMetrics() {
        Set<Metric> metrics =new LinkedHashSet<>();

        metrics.add(new Metric("cluster-controller.down.count.last"));
        metrics.add(new Metric("cluster-controller.initializing.count.last"));
        metrics.add(new Metric("cluster-controller.maintenance.count.last"));
        metrics.add(new Metric("cluster-controller.retired.count.last"));
        metrics.add(new Metric("cluster-controller.stopping.count.last"));
        metrics.add(new Metric("cluster-controller.up.count.last"));
        metrics.add(new Metric("cluster-controller.cluster-state-change.count"));

        metrics.add(new Metric("cluster-controller.is-master.last"));
        // TODO(hakonhall): Update this name once persistent "count" metrics has been implemented.
        // DO NOT RELY ON THIS METRIC YET.
        metrics.add(new Metric("cluster-controller.node-event.count"));

        metrics.add(new Metric("cluster-controller.resource_usage.nodes_above_limit.last"));
        metrics.add(new Metric("cluster-controller.resource_usage.max_memory_utilization.last"));
        metrics.add(new Metric("cluster-controller.resource_usage.max_disk_utilization.last"));
        metrics.add(new Metric("cluster-controller.resource_usage.disk_limit.last"));
        metrics.add(new Metric("cluster-controller.resource_usage.memory_limit.last"));

        metrics.add(new Metric("reindexing.progress.last"));

        return metrics;
    }

    private static Set<Metric> getDocprocMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        // per chain
        metrics.add(new Metric("documents_processed.rate"));

        return metrics;
    }

    private static Set<Metric> getQrserverMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("peak_qps.max"));
        metrics.add(new Metric("search_connections.max"));
        metrics.add(new Metric("search_connections.sum"));
        metrics.add(new Metric("search_connections.count"));
        metrics.add(new Metric("search_connections.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("active_queries.max"));
        metrics.add(new Metric("active_queries.sum"));
        metrics.add(new Metric("active_queries.count"));
        metrics.add(new Metric("active_queries.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("feed.latency.max"));
        metrics.add(new Metric("feed.latency.sum"));
        metrics.add(new Metric("feed.latency.count"));
        metrics.add(new Metric("feed.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("feed.http-requests.count"));
        metrics.add(new Metric("feed.http-requests.rate"));
        metrics.add(new Metric("queries.rate"));
        metrics.add(new Metric("query_container_latency.max"));
        metrics.add(new Metric("query_container_latency.sum"));
        metrics.add(new Metric("query_container_latency.count"));
        metrics.add(new Metric("query_container_latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("query_latency.max"));
        metrics.add(new Metric("query_latency.sum"));
        metrics.add(new Metric("query_latency.count"));
        metrics.add(new Metric("query_latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("query_latency.95percentile"));
        metrics.add(new Metric("query_latency.99percentile"));
        metrics.add(new Metric("failed_queries.rate"));
        metrics.add(new Metric("degraded_queries.rate"));
        metrics.add(new Metric("hits_per_query.max"));
        metrics.add(new Metric("hits_per_query.sum"));
        metrics.add(new Metric("hits_per_query.count"));
        metrics.add(new Metric("hits_per_query.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("hits_per_query.95percentile"));
        metrics.add(new Metric("hits_per_query.99percentile"));
        metrics.add(new Metric("query_hit_offset.max"));
        metrics.add(new Metric("query_hit_offset.sum"));
        metrics.add(new Metric("query_hit_offset.count"));
        metrics.add(new Metric("documents_covered.count"));
        metrics.add(new Metric("documents_total.count"));
        metrics.add(new Metric("dispatch_internal.rate"));
        metrics.add(new Metric("dispatch_fdispatch.rate"));

        metrics.add(new Metric("totalhits_per_query.max"));
        metrics.add(new Metric("totalhits_per_query.sum"));
        metrics.add(new Metric("totalhits_per_query.count"));
        metrics.add(new Metric("totalhits_per_query.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("totalhits_per_query.95percentile"));
        metrics.add(new Metric("totalhits_per_query.99percentile"));
        metrics.add(new Metric("empty_results.rate"));
        metrics.add(new Metric("requestsOverQuota.rate"));
        metrics.add(new Metric("requestsOverQuota.count"));

        metrics.add(new Metric("relevance.at_1.sum"));
        metrics.add(new Metric("relevance.at_1.count"));
        metrics.add(new Metric("relevance.at_1.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("relevance.at_3.sum"));
        metrics.add(new Metric("relevance.at_3.count"));
        metrics.add(new Metric("relevance.at_3.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("relevance.at_10.sum"));
        metrics.add(new Metric("relevance.at_10.count"));
        metrics.add(new Metric("relevance.at_10.average")); // TODO: Remove in Vespa 8

        // Errors from qrserver
        metrics.add(new Metric("error.timeout.rate"));
        metrics.add(new Metric("error.backends_oos.rate"));
        metrics.add(new Metric("error.plugin_failure.rate"));
        metrics.add(new Metric("error.backend_communication_error.rate"));
        metrics.add(new Metric("error.empty_document_summaries.rate"));
        metrics.add(new Metric("error.invalid_query_parameter.rate"));
        metrics.add(new Metric("error.internal_server_error.rate"));
        metrics.add(new Metric("error.misconfigured_server.rate"));
        metrics.add(new Metric("error.invalid_query_transformation.rate"));
        metrics.add(new Metric("error.result_with_errors.rate"));
        metrics.add(new Metric("error.unspecified.rate"));
        metrics.add(new Metric("error.unhandled_exception.rate"));

        return metrics;
    }

    private static void addSearchNodeExecutorMetrics(Set<Metric> metrics, String prefix) {
        metrics.add(new Metric(prefix + ".queuesize.max"));
        metrics.add(new Metric(prefix + ".queuesize.sum"));
        metrics.add(new Metric(prefix + ".queuesize.count"));
        metrics.add(new Metric(prefix + ".maxpending.last")); // TODO: Remove in Vespa 8
        metrics.add(new Metric(prefix + ".accepted.rate"));
    }

    private static Set<Metric> getSearchNodeMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("content.proton.documentdb.documents.total.last"));
        metrics.add(new Metric("content.proton.documentdb.documents.ready.last"));
        metrics.add(new Metric("content.proton.documentdb.documents.active.last"));
        metrics.add(new Metric("content.proton.documentdb.documents.removed.last"));

        metrics.add(new Metric("content.proton.documentdb.index.docs_in_memory.last"));
        metrics.add(new Metric("content.proton.documentdb.disk_usage.last"));
        metrics.add(new Metric("content.proton.documentdb.memory_usage.allocated_bytes.max"));
        metrics.add(new Metric("content.proton.transport.query.count.rate"));
        metrics.add(new Metric("content.proton.docsum.docs.rate"));
        metrics.add(new Metric("content.proton.docsum.latency.max"));
        metrics.add(new Metric("content.proton.docsum.latency.sum"));
        metrics.add(new Metric("content.proton.docsum.latency.count"));
        metrics.add(new Metric("content.proton.docsum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.transport.query.latency.max"));
        metrics.add(new Metric("content.proton.transport.query.latency.sum"));
        metrics.add(new Metric("content.proton.transport.query.latency.count"));
        metrics.add(new Metric("content.proton.transport.query.latency.average")); // TODO: Remove in Vespa 8

        // Search protocol
        metrics.add(new Metric("content.proton.search_protocol.query.latency.max"));
        metrics.add(new Metric("content.proton.search_protocol.query.latency.sum"));
        metrics.add(new Metric("content.proton.search_protocol.query.latency.count"));
        metrics.add(new Metric("content.proton.search_protocol.query.request_size.max"));
        metrics.add(new Metric("content.proton.search_protocol.query.request_size.sum"));
        metrics.add(new Metric("content.proton.search_protocol.query.request_size.count"));
        metrics.add(new Metric("content.proton.search_protocol.query.reply_size.max"));
        metrics.add(new Metric("content.proton.search_protocol.query.reply_size.sum"));
        metrics.add(new Metric("content.proton.search_protocol.query.reply_size.count"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.latency.max"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.latency.sum"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.latency.count"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.request_size.max"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.request_size.sum"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.request_size.count"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.reply_size.max"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.reply_size.sum"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.reply_size.count"));
        metrics.add(new Metric("content.proton.search_protocol.docsum.requested_documents.count"));        
        
        // Executors shared between all document dbs
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.proton");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.flush");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.match");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.docsum");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.shared");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.warmup");

        // jobs
        metrics.add(new Metric("content.proton.documentdb.job.total.average"));
        metrics.add(new Metric("content.proton.documentdb.job.attribute_flush.average"));
        metrics.add(new Metric("content.proton.documentdb.job.memory_index_flush.average"));
        metrics.add(new Metric("content.proton.documentdb.job.disk_index_fusion.average"));
        metrics.add(new Metric("content.proton.documentdb.job.document_store_flush.average"));
        metrics.add(new Metric("content.proton.documentdb.job.document_store_compact.average"));
        metrics.add(new Metric("content.proton.documentdb.job.bucket_move.average"));
        metrics.add(new Metric("content.proton.documentdb.job.lid_space_compact.average"));
        metrics.add(new Metric("content.proton.documentdb.job.removed_documents_prune.average"));

        // Threading service (per document db)
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.master");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.index");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.summary");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.index_field_inverter");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.index_field_writer");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.attribute_field_writer");

        // lid space
        metrics.add(new Metric("content.proton.documentdb.ready.lid_space.lid_bloat_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.lid_space.lid_bloat_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.lid_space.lid_bloat_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.lid_space.lid_fragmentation_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.lid_space.lid_fragmentation_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.lid_space.lid_fragmentation_factor.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.lid_space.lid_limit.last"));
        metrics.add(new Metric("content.proton.documentdb.notready.lid_space.lid_limit.last"));
        metrics.add(new Metric("content.proton.documentdb.removed.lid_space.lid_limit.last"));
        metrics.add(new Metric("content.proton.documentdb.ready.lid_space.highest_used_lid.last"));
        metrics.add(new Metric("content.proton.documentdb.notready.lid_space.highest_used_lid.last"));
        metrics.add(new Metric("content.proton.documentdb.removed.lid_space.highest_used_lid.last"));
        metrics.add(new Metric("content.proton.documentdb.ready.lid_space.used_lids.last"));
        metrics.add(new Metric("content.proton.documentdb.notready.lid_space.used_lids.last"));
        metrics.add(new Metric("content.proton.documentdb.removed.lid_space.used_lids.last"));

        // bucket move
        metrics.add(new Metric("content.proton.documentdb.bucket_move.buckets_pending.last"));

        // resource usage
        metrics.add(new Metric("content.proton.resource_usage.disk.average"));
        metrics.add(new Metric("content.proton.resource_usage.disk_utilization.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory_utilization.average"));
        metrics.add(new Metric("content.proton.resource_usage.transient_memory.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory_mappings.max"));
        metrics.add(new Metric("content.proton.resource_usage.open_file_descriptors.max"));
        metrics.add(new Metric("content.proton.resource_usage.feeding_blocked.max"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.enum_store.average"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.multi_value.average"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.feeding_blocked.last")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.feeding_blocked.max"));

        // transaction log
        metrics.add(new Metric("content.proton.transactionlog.entries.average"));
        metrics.add(new Metric("content.proton.transactionlog.disk_usage.average"));
        metrics.add(new Metric("content.proton.transactionlog.replay_time.last"));

        // document store
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.disk_usage.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.disk_bloat.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.max_bucket_spread.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.memory_usage.allocated_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.memory_usage.used_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.memory_usage.dead_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.memory_usage.onhold_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.disk_usage.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.disk_bloat.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.max_bucket_spread.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.memory_usage.allocated_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.memory_usage.used_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.memory_usage.dead_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.memory_usage.onhold_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.disk_usage.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.disk_bloat.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.max_bucket_spread.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.memory_usage.allocated_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.memory_usage.used_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.memory_usage.dead_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.removed.document_store.memory_usage.onhold_bytes.average"));

        // document store cache
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.cache.memory_usage.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.cache.hit_rate.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.cache.lookups.rate"));
        metrics.add(new Metric("content.proton.documentdb.ready.document_store.cache.invalidations.rate"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.cache.memory_usage.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.cache.hit_rate.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.cache.lookups.rate"));
        metrics.add(new Metric("content.proton.documentdb.notready.document_store.cache.invalidations.rate"));

        // attribute
        metrics.add(new Metric("content.proton.documentdb.ready.attribute.memory_usage.allocated_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.attribute.memory_usage.used_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.attribute.memory_usage.dead_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.ready.attribute.memory_usage.onhold_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.attribute.memory_usage.allocated_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.attribute.memory_usage.used_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.attribute.memory_usage.dead_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.notready.attribute.memory_usage.onhold_bytes.average"));

        // index
        metrics.add(new Metric("content.proton.documentdb.index.memory_usage.allocated_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.index.memory_usage.used_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.index.memory_usage.dead_bytes.average"));
        metrics.add(new Metric("content.proton.documentdb.index.memory_usage.onhold_bytes.average"));

        // matching
        metrics.add(new Metric("content.proton.documentdb.matching.queries.rate"));
        metrics.add(new Metric("content.proton.documentdb.matching.soft_doomed_queries.rate"));
        metrics.add(new Metric("content.proton.documentdb.matching.query_latency.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.query_latency.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.query_latency.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.query_latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.query_collateral_time.max")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.query_collateral_time.sum")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.query_collateral_time.count")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.query_collateral_time.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.query_setup_time.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.query_setup_time.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.query_setup_time.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.docs_matched.rate")); // TODO: Consider remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.docs_matched.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.docs_matched.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.docs_matched.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.queries.rate"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.soft_doomed_queries.rate"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.soft_doom_factor.min"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.soft_doom_factor.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.soft_doom_factor.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.soft_doom_factor.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_latency.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_latency.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_latency.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_collateral_time.max")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_collateral_time.sum")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_collateral_time.count")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_collateral_time.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.docs_matched.rate")); // TODO: Consider remove in Vespa 8
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.docs_matched.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.docs_matched.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.docs_matched.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.limited_queries.rate"));

        return metrics;
    }

    private static Set<Metric> getStorageMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        // TODO: For the purpose of this file and likely elsewhere, all but the last aggregate specifier,
        // TODO: such as 'average' and 'sum' in the metric names below are just confusing and can be mentally
        // TODO: disregarded when considering metric names. Consider cleaning up for Vespa 8.
        // TODO Vespa 8 all metrics with .sum in the name should have that removed.
        metrics.add(new Metric("vds.datastored.alldisks.docs.average"));
        metrics.add(new Metric("vds.datastored.alldisks.bytes.average"));
        metrics.add(new Metric("vds.visitor.allthreads.averagevisitorlifetime.sum.max"));
        metrics.add(new Metric("vds.visitor.allthreads.averagevisitorlifetime.sum.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.averagevisitorlifetime.sum.count"));
        metrics.add(new Metric("vds.visitor.allthreads.averagevisitorlifetime.sum.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.visitor.allthreads.averagequeuewait.sum.max"));
        metrics.add(new Metric("vds.visitor.allthreads.averagequeuewait.sum.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.averagequeuewait.sum.count"));
        metrics.add(new Metric("vds.visitor.allthreads.averagequeuewait.sum.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.createiterator.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.visit.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove_location.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.queuesize.max"));
        metrics.add(new Metric("vds.filestor.alldisks.queuesize.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.queuesize.count"));
        metrics.add(new Metric("vds.filestor.alldisks.queuesize.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.averagequeuewait.sum.max"));
        metrics.add(new Metric("vds.filestor.alldisks.averagequeuewait.sum.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.averagequeuewait.sum.count"));
        metrics.add(new Metric("vds.filestor.alldisks.averagequeuewait.sum.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.mergemetadatareadlatency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.mergemetadatareadlatency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.mergemetadatareadlatency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.mergedatareadlatency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.mergedatareadlatency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.mergedatareadlatency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.mergedatawritelatency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.mergedatawritelatency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.mergedatawritelatency.count"));

        metrics.add(new Metric("vds.visitor.allthreads.queuesize.count.max"));
        metrics.add(new Metric("vds.visitor.allthreads.queuesize.count.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.queuesize.count.count"));
        metrics.add(new Metric("vds.visitor.allthreads.queuesize.count.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.visitor.allthreads.completed.sum.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.visitor.allthreads.completed.sum.rate"));
        metrics.add(new Metric("vds.visitor.allthreads.created.sum.rate"));
        metrics.add(new Metric("vds.visitor.allthreads.failed.sum.rate"));
        metrics.add(new Metric("vds.visitor.allthreads.averagemessagesendtime.sum.max"));
        metrics.add(new Metric("vds.visitor.allthreads.averagemessagesendtime.sum.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.averagemessagesendtime.sum.count"));
        metrics.add(new Metric("vds.visitor.allthreads.averagemessagesendtime.sum.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.visitor.allthreads.averageprocessingtime.sum.max"));
        metrics.add(new Metric("vds.visitor.allthreads.averageprocessingtime.sum.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.averageprocessingtime.sum.count"));
        metrics.add(new Metric("vds.visitor.allthreads.averageprocessingtime.sum.average")); // TODO: Remove in Vespa 8
        
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.failed.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.latency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.latency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.latency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.request_size.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.request_size.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.request_size.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.failed.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.latency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.latency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.latency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.request_size.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.request_size.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.request_size.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.failed.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.latency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.latency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.latency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.request_size.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.request_size.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.request_size.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.failed.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.latency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.latency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.latency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.request_size.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.request_size.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.request_size.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.createiterator.latency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.createiterator.latency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.createiterator.latency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.createiterator.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.visit.sum.latency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.visit.sum.latency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.visit.sum.latency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.visit.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove_location.sum.latency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove_location.sum.latency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove_location.sum.latency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove_location.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.splitbuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.joinbuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.deletebuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.deletebuckets.failed.rate"));        
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.deletebuckets.latency.max"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.deletebuckets.latency.sum"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.deletebuckets.latency.count"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.deletebuckets.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.setbucketstates.count.rate"));

        //Distributor
        metrics.add(new Metric("vds.idealstate.buckets_rechecking.average"));
        metrics.add(new Metric("vds.idealstate.idealstate_diff.average"));
        metrics.add(new Metric("vds.idealstate.buckets_toofewcopies.average"));
        metrics.add(new Metric("vds.idealstate.buckets_toomanycopies.average"));
        metrics.add(new Metric("vds.idealstate.buckets.average"));
        metrics.add(new Metric("vds.idealstate.buckets_notrusted.average"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.done_ok.rate"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.done_failed.rate"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.pending.average"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.done_ok.rate"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.done_failed.rate"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.pending.average"));
        metrics.add(new Metric("vds.idealstate.split_bucket.done_ok.rate"));
        metrics.add(new Metric("vds.idealstate.split_bucket.done_failed.rate"));
        metrics.add(new Metric("vds.idealstate.split_bucket.pending.average"));
        metrics.add(new Metric("vds.idealstate.join_bucket.done_ok.rate"));
        metrics.add(new Metric("vds.idealstate.join_bucket.done_failed.rate"));
        metrics.add(new Metric("vds.idealstate.join_bucket.pending.average"));
        metrics.add(new Metric("vds.idealstate.garbage_collection.done_ok.rate"));
        metrics.add(new Metric("vds.idealstate.garbage_collection.done_failed.rate"));
        metrics.add(new Metric("vds.idealstate.garbage_collection.pending.average"));
        metrics.add(new Metric("vds.idealstate.garbage_collection.documents_removed.count"));
        metrics.add(new Metric("vds.idealstate.garbage_collection.documents_removed.rate"));

        metrics.add(new Metric("vds.distributor.puts.sum.latency.max"));
        metrics.add(new Metric("vds.distributor.puts.sum.latency.sum"));
        metrics.add(new Metric("vds.distributor.puts.sum.latency.count"));
        metrics.add(new Metric("vds.distributor.puts.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.distributor.puts.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.puts.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.puts.sum.failures.notfound.rate"));
        metrics.add(new Metric("vds.distributor.puts.sum.failures.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.distributor.puts.sum.failures.concurrent_mutations.rate"));
        metrics.add(new Metric("vds.distributor.removes.sum.latency.max"));
        metrics.add(new Metric("vds.distributor.removes.sum.latency.sum"));
        metrics.add(new Metric("vds.distributor.removes.sum.latency.count"));
        metrics.add(new Metric("vds.distributor.removes.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.distributor.removes.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.removes.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.removes.sum.failures.notfound.rate"));
        metrics.add(new Metric("vds.distributor.removes.sum.failures.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.distributor.removes.sum.failures.concurrent_mutations.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.latency.max"));
        metrics.add(new Metric("vds.distributor.updates.sum.latency.sum"));
        metrics.add(new Metric("vds.distributor.updates.sum.latency.count"));
        metrics.add(new Metric("vds.distributor.updates.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.distributor.updates.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.failures.notfound.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.failures.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.failures.concurrent_mutations.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.diverging_timestamp_updates.rate"));
        metrics.add(new Metric("vds.distributor.removelocations.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.removelocations.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.gets.sum.latency.max"));
        metrics.add(new Metric("vds.distributor.gets.sum.latency.sum"));
        metrics.add(new Metric("vds.distributor.gets.sum.latency.count"));
        metrics.add(new Metric("vds.distributor.gets.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.distributor.gets.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.gets.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.gets.sum.failures.notfound.rate"));
        metrics.add(new Metric("vds.distributor.visitor.sum.latency.max"));
        metrics.add(new Metric("vds.distributor.visitor.sum.latency.sum"));
        metrics.add(new Metric("vds.distributor.visitor.sum.latency.count"));
        metrics.add(new Metric("vds.distributor.visitor.sum.latency.average")); // TODO: Remove in Vespa 8
        metrics.add(new Metric("vds.distributor.visitor.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.visitor.sum.failures.total.rate"));

        metrics.add(new Metric("vds.distributor.docsstored.average"));
        metrics.add(new Metric("vds.distributor.bytesstored.average"));

        metrics.add(new Metric("vds.bouncer.clock_skew_aborts.count"));

        return metrics;
    }

    private static void addMetric(Set<Metric> metrics, String metricName, List<String> aggregateSuffices) {
        for (String suffix : aggregateSuffices) {
            metrics.add(new Metric(metricName + "." + suffix));
        }
    }

}
