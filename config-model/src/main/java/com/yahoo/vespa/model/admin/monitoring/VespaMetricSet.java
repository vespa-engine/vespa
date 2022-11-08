// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        metrics.addAll(getDistributorMetrics());
        metrics.addAll(getDocprocMetrics());
        metrics.addAll(getClusterControllerMetrics());
        metrics.addAll(getSearchChainMetrics());
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
        metrics.add(new Metric("worker.connections.max"));
        metrics.add(new Metric("endpoint.certificate.expiry.seconds"));

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

        // Node certificate
        metrics.add(new Metric("node-certificate.expiry.seconds"));

        return metrics;
    }

    private static Set<Metric> getConfigServerMetrics() {
        Set<Metric> metrics =new LinkedHashSet<>();

        metrics.add(new Metric("configserver.requests.count"));
        metrics.add(new Metric("configserver.failedRequests.count"));
        metrics.add(new Metric("configserver.latency.max"));
        metrics.add(new Metric("configserver.latency.sum"));
        metrics.add(new Metric("configserver.latency.count"));
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

        metrics.add(new Metric("serverRejectedRequests.rate"));
        metrics.add(new Metric("serverRejectedRequests.count"));

        metrics.add(new Metric("serverThreadPoolSize.max"));
        metrics.add(new Metric("serverThreadPoolSize.last"));

        metrics.add(new Metric("serverActiveThreads.min"));
        metrics.add(new Metric("serverActiveThreads.max"));
        metrics.add(new Metric("serverActiveThreads.sum"));
        metrics.add(new Metric("serverActiveThreads.count"));
        metrics.add(new Metric("serverActiveThreads.last"));

        metrics.add(new Metric("serverNumOpenConnections.average"));
        metrics.add(new Metric("serverNumOpenConnections.max"));
        metrics.add(new Metric("serverNumOpenConnections.last"));
        metrics.add(new Metric("serverNumConnections.average"));
        metrics.add(new Metric("serverNumConnections.max"));
        metrics.add(new Metric("serverNumConnections.last"));

        metrics.add(new Metric("serverBytesReceived.sum"));
        metrics.add(new Metric("serverBytesReceived.count"));
        metrics.add(new Metric("serverBytesSent.sum"));
        metrics.add(new Metric("serverBytesSent.count"));

        {
            List<String> suffixes = List.of("sum", "count", "last", "min", "max");
            addMetric(metrics, "jdisc.thread_pool.unhandled_exceptions", suffixes);
            addMetric(metrics, "jdisc.thread_pool.work_queue.capacity", suffixes);
            addMetric(metrics, "jdisc.thread_pool.work_queue.size", suffixes);
            addMetric(metrics, "jdisc.thread_pool.rejected_tasks", suffixes);
            addMetric(metrics, "jdisc.thread_pool.size", suffixes);
            addMetric(metrics, "jdisc.thread_pool.max_allowed_size", suffixes);
            addMetric(metrics, "jdisc.thread_pool.active_threads", suffixes);

            addMetric(metrics, "jdisc.http.jetty.threadpool.thread.max", suffixes);
            addMetric(metrics, "jdisc.http.jetty.threadpool.thread.min", suffixes);
            addMetric(metrics, "jdisc.http.jetty.threadpool.thread.reserved", suffixes);
            addMetric(metrics, "jdisc.http.jetty.threadpool.thread.busy", suffixes);
            addMetric(metrics, "jdisc.http.jetty.threadpool.thread.total", suffixes);
            addMetric(metrics, "jdisc.http.jetty.threadpool.queue.size", suffixes);
        }

        metrics.add(new Metric("httpapi_latency.max"));
        metrics.add(new Metric("httpapi_latency.sum"));
        metrics.add(new Metric("httpapi_latency.count"));
        metrics.add(new Metric("httpapi_pending.max"));
        metrics.add(new Metric("httpapi_pending.sum"));
        metrics.add(new Metric("httpapi_pending.count"));
        metrics.add(new Metric("httpapi_num_operations.rate"));
        metrics.add(new Metric("httpapi_num_updates.rate"));
        metrics.add(new Metric("httpapi_num_removes.rate"));
        metrics.add(new Metric("httpapi_num_puts.rate"));
        metrics.add(new Metric("httpapi_succeeded.rate"));
        metrics.add(new Metric("httpapi_failed.rate"));
        metrics.add(new Metric("httpapi_parse_error.rate"));
        addMetric(metrics, "httpapi_condition_not_met", List.of("rate"));
        addMetric(metrics, "httpapi_not_found", List.of("rate"));

        metrics.add(new Metric("mem.heap.total.average"));
        metrics.add(new Metric("mem.heap.free.average"));
        metrics.add(new Metric("mem.heap.used.average"));
        metrics.add(new Metric("mem.heap.used.max"));
        metrics.add(new Metric("jdisc.memory_mappings.max"));
        metrics.add(new Metric("jdisc.open_file_descriptors.max"));
        metrics.add(new Metric("mem.direct.total.average"));
        metrics.add(new Metric("mem.direct.free.average"));
        metrics.add(new Metric("mem.direct.used.average"));
        metrics.add(new Metric("mem.direct.used.max"));
        metrics.add(new Metric("mem.direct.count.max"));
        metrics.add(new Metric("mem.native.total.average"));
        metrics.add(new Metric("mem.native.free.average"));
        metrics.add(new Metric("mem.native.used.average"));
        metrics.add(new Metric("mem.native.used.max"));

        metrics.add(new Metric("jdisc.gc.count.average"));
        metrics.add(new Metric("jdisc.gc.count.max"));
        metrics.add(new Metric("jdisc.gc.count.last"));
        metrics.add(new Metric("jdisc.gc.ms.average"));
        metrics.add(new Metric("jdisc.gc.ms.max"));
        metrics.add(new Metric("jdisc.gc.ms.last"));

        metrics.add(new Metric("jdisc.deactivated_containers.total.last"));
        metrics.add(new Metric("jdisc.deactivated_containers.with_retained_refs.last"));

        metrics.add(new Metric("jdisc.singleton.is_active.last"));
        metrics.add(new Metric("jdisc.singleton.activation.count.last"));
        metrics.add(new Metric("jdisc.singleton.activation.failure.count.last"));
        metrics.add(new Metric("jdisc.singleton.activation.millis.last"));
        metrics.add(new Metric("jdisc.singleton.deactivation.count.last"));
        metrics.add(new Metric("jdisc.singleton.deactivation.failure.count.last"));
        metrics.add(new Metric("jdisc.singleton.deactivation.millis.last"));

        metrics.add(new Metric("athenz-tenant-cert.expiry.seconds.last"));
        metrics.add(new Metric("container-iam-role.expiry.seconds"));

        metrics.add(new Metric("jdisc.http.request.prematurely_closed.rate"));
        addMetric(metrics, "jdisc.http.request.requests_per_connection", List.of("sum", "count", "min", "max", "average"));

        metrics.add(new Metric("http.status.1xx.rate"));
        metrics.add(new Metric("http.status.2xx.rate"));
        metrics.add(new Metric("http.status.3xx.rate"));
        metrics.add(new Metric("http.status.4xx.rate"));
        metrics.add(new Metric("http.status.5xx.rate"));

        metrics.add(new Metric("jdisc.http.request.uri_length.max"));
        metrics.add(new Metric("jdisc.http.request.uri_length.sum"));
        metrics.add(new Metric("jdisc.http.request.uri_length.count"));
        metrics.add(new Metric("jdisc.http.request.content_size.max"));
        metrics.add(new Metric("jdisc.http.request.content_size.sum"));
        metrics.add(new Metric("jdisc.http.request.content_size.count"));

        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.missing_client_cert.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.expired_client_cert.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.invalid_client_cert.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.incompatible_protocols.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.incompatible_ciphers.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.unknown.rate"));
        metrics.add(new Metric("jdisc.http.ssl.handshake.failure.connection_closed.rate"));

        metrics.add(new Metric("jdisc.http.handler.unhandled_exceptions.rate"));

        addMetric(metrics, "jdisc.http.filtering.request.handled", List.of("rate"));
        addMetric(metrics, "jdisc.http.filtering.request.unhandled", List.of("rate"));
        addMetric(metrics, "jdisc.http.filtering.response.handled", List.of("rate"));
        addMetric(metrics, "jdisc.http.filtering.response.unhandled", List.of("rate"));

        addMetric(metrics, "jdisc.application.failed_component_graphs", List.of("rate"));

        addMetric(metrics, "jdisc.http.filter.rule.blocked_requests", List.of("rate"));
        addMetric(metrics, "jdisc.http.filter.rule.allowed_requests", List.of("rate"));
        addMetric(metrics, "jdisc.jvm", List.of("last"));

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
        metrics.add(new Metric("cluster-controller.busy-tick-time-ms.last"));
        metrics.add(new Metric("cluster-controller.busy-tick-time-ms.max"));
        metrics.add(new Metric("cluster-controller.busy-tick-time-ms.sum"));
        metrics.add(new Metric("cluster-controller.busy-tick-time-ms.count"));
        metrics.add(new Metric("cluster-controller.idle-tick-time-ms.last"));
        metrics.add(new Metric("cluster-controller.idle-tick-time-ms.max"));
        metrics.add(new Metric("cluster-controller.idle-tick-time-ms.sum"));
        metrics.add(new Metric("cluster-controller.idle-tick-time-ms.count"));

        metrics.add(new Metric("cluster-controller.work-ms.last"));
        metrics.add(new Metric("cluster-controller.work-ms.sum"));
        metrics.add(new Metric("cluster-controller.work-ms.count"));

        metrics.add(new Metric("cluster-controller.is-master.last"));
        metrics.add(new Metric("cluster-controller.remote-task-queue.size.last"));
        // TODO(hakonhall): Update this name once persistent "count" metrics has been implemented.
        // DO NOT RELY ON THIS METRIC YET.
        metrics.add(new Metric("cluster-controller.node-event.count"));

        metrics.add(new Metric("cluster-controller.resource_usage.nodes_above_limit.last"));
        metrics.add(new Metric("cluster-controller.resource_usage.nodes_above_limit.max"));
        metrics.add(new Metric("cluster-controller.resource_usage.max_memory_utilization.last"));
        metrics.add(new Metric("cluster-controller.resource_usage.max_memory_utilization.max"));
        metrics.add(new Metric("cluster-controller.resource_usage.max_disk_utilization.last"));
        metrics.add(new Metric("cluster-controller.resource_usage.max_disk_utilization.max"));
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

    private static Set<Metric> getSearchChainMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("peak_qps.max"));
        metrics.add(new Metric("search_connections.max"));
        metrics.add(new Metric("search_connections.sum"));
        metrics.add(new Metric("search_connections.count"));
        metrics.add(new Metric("feed.latency.max"));
        metrics.add(new Metric("feed.latency.sum"));
        metrics.add(new Metric("feed.latency.count"));
        metrics.add(new Metric("feed.http-requests.count"));
        metrics.add(new Metric("feed.http-requests.rate"));
        metrics.add(new Metric("queries.rate"));
        metrics.add(new Metric("query_container_latency.max"));
        metrics.add(new Metric("query_container_latency.sum"));
        metrics.add(new Metric("query_container_latency.count"));
        metrics.add(new Metric("query_latency.max"));
        metrics.add(new Metric("query_latency.sum"));
        metrics.add(new Metric("query_latency.count"));
        metrics.add(new Metric("query_latency.95percentile"));
        metrics.add(new Metric("query_latency.99percentile"));
        metrics.add(new Metric("failed_queries.rate"));
        metrics.add(new Metric("degraded_queries.rate"));
        metrics.add(new Metric("hits_per_query.max"));
        metrics.add(new Metric("hits_per_query.sum"));
        metrics.add(new Metric("hits_per_query.count"));
        metrics.add(new Metric("hits_per_query.95percentile"));
        metrics.add(new Metric("hits_per_query.99percentile"));
        metrics.add(new Metric("query_hit_offset.max"));
        metrics.add(new Metric("query_hit_offset.sum"));
        metrics.add(new Metric("query_hit_offset.count"));
        metrics.add(new Metric("documents_covered.count"));
        metrics.add(new Metric("documents_total.count"));
        metrics.add(new Metric("documents_target_total.count"));
        addMetric(metrics, "jdisc.render.latency", Set.of("min", "max", "count", "sum", "last", "average"));
        addMetric(metrics, "query_item_count", Set.of("max", "sum", "count"));

        metrics.add(new Metric("totalhits_per_query.max"));
        metrics.add(new Metric("totalhits_per_query.sum"));
        metrics.add(new Metric("totalhits_per_query.count"));
        metrics.add(new Metric("totalhits_per_query.95percentile"));
        metrics.add(new Metric("totalhits_per_query.99percentile"));
        metrics.add(new Metric("empty_results.rate"));
        metrics.add(new Metric("requestsOverQuota.rate"));
        metrics.add(new Metric("requestsOverQuota.count"));

        metrics.add(new Metric("relevance.at_1.sum"));
        metrics.add(new Metric("relevance.at_1.count"));
        metrics.add(new Metric("relevance.at_3.sum"));
        metrics.add(new Metric("relevance.at_3.count"));
        metrics.add(new Metric("relevance.at_10.sum"));
        metrics.add(new Metric("relevance.at_10.count"));

        // Errors from search container
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
        metrics.add(new Metric(prefix + ".accepted.rate"));
        metrics.add(new Metric(prefix + ".wakeups.rate"));
        metrics.add(new Metric(prefix + ".utilization.max"));
        metrics.add(new Metric(prefix + ".utilization.sum"));
        metrics.add(new Metric(prefix + ".utilization.count"));
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
        metrics.add(new Metric("content.proton.documentdb.heart_beat_age.last"));
        metrics.add(new Metric("content.proton.transport.query.count.rate"));
        metrics.add(new Metric("content.proton.docsum.docs.rate"));
        metrics.add(new Metric("content.proton.docsum.latency.max"));
        metrics.add(new Metric("content.proton.docsum.latency.sum"));
        metrics.add(new Metric("content.proton.docsum.latency.count"));
        metrics.add(new Metric("content.proton.transport.query.latency.max"));
        metrics.add(new Metric("content.proton.transport.query.latency.sum"));
        metrics.add(new Metric("content.proton.transport.query.latency.count"));

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
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.field_writer");

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
        metrics.add(new Metric("content.proton.resource_usage.disk_usage.total.max"));
        metrics.add(new Metric("content.proton.resource_usage.disk_usage.total_utilization.max"));
        metrics.add(new Metric("content.proton.resource_usage.disk_usage.transient.max"));
        metrics.add(new Metric("content.proton.resource_usage.memory.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory_usage.total.max"));
        metrics.add(new Metric("content.proton.resource_usage.memory_usage.total_utilization.max"));
        metrics.add(new Metric("content.proton.resource_usage.memory_usage.transient.max"));
        metrics.add(new Metric("content.proton.resource_usage.memory_mappings.max"));
        metrics.add(new Metric("content.proton.resource_usage.open_file_descriptors.max"));
        metrics.add(new Metric("content.proton.resource_usage.feeding_blocked.max"));
        metrics.add(new Metric("content.proton.resource_usage.malloc_arena.max"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.address_space.max"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.feeding_blocked.max"));

        // CPU util
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.setup.max"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.setup.sum"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.setup.count"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.read.max"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.read.sum"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.read.count"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.write.max"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.write.sum"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.write.count"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.compact.max"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.compact.sum"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.compact.count"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.other.max"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.other.sum"));
        metrics.add(new Metric("content.proton.resource_usage.cpu_util.other.count"));

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
        metrics.add(new Metric("content.proton.documentdb.matching.query_setup_time.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.query_setup_time.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.query_setup_time.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.docs_matched.rate"));
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
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.query_setup_time.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.grouping_time.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.grouping_time.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.grouping_time.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.max"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.sum"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.rerank_time.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.docs_matched.rate"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.docs_matched.count"));
        metrics.add(new Metric("content.proton.documentdb.matching.rank_profile.limited_queries.rate"));

        // feeding
        metrics.add(new Metric("content.proton.documentdb.feeding.commit.operations.max"));
        metrics.add(new Metric("content.proton.documentdb.feeding.commit.operations.sum"));
        metrics.add(new Metric("content.proton.documentdb.feeding.commit.operations.count"));
        metrics.add(new Metric("content.proton.documentdb.feeding.commit.operations.rate"));
        metrics.add(new Metric("content.proton.documentdb.feeding.commit.latency.max"));
        metrics.add(new Metric("content.proton.documentdb.feeding.commit.latency.sum"));
        metrics.add(new Metric("content.proton.documentdb.feeding.commit.latency.count"));

        return metrics;
    }

    private static Set<Metric> getStorageMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        // TODO: For the purpose of this file and likely elsewhere, all but the last aggregate specifier,
        // TODO: such as 'average' and 'sum' in the metric names below are just confusing and can be mentally
        // TODO: disregarded when considering metric names. Consider cleaning up for Vespa 9.
        metrics.add(new Metric("vds.datastored.alldisks.buckets.average"));
        metrics.add(new Metric("vds.datastored.alldisks.docs.average"));
        metrics.add(new Metric("vds.datastored.alldisks.bytes.average"));
        metrics.add(new Metric("vds.visitor.allthreads.averagevisitorlifetime.max"));
        metrics.add(new Metric("vds.visitor.allthreads.averagevisitorlifetime.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.averagevisitorlifetime.count"));
        metrics.add(new Metric("vds.visitor.allthreads.averagequeuewait.max"));
        metrics.add(new Metric("vds.visitor.allthreads.averagequeuewait.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.averagequeuewait.count"));
        metrics.add(new Metric("vds.visitor.allthreads.queuesize.max"));
        metrics.add(new Metric("vds.visitor.allthreads.queuesize.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.queuesize.count"));
        metrics.add(new Metric("vds.visitor.allthreads.completed.rate"));
        metrics.add(new Metric("vds.visitor.allthreads.created.rate"));
        metrics.add(new Metric("vds.visitor.allthreads.failed.rate"));
        metrics.add(new Metric("vds.visitor.allthreads.averagemessagesendtime.max"));
        metrics.add(new Metric("vds.visitor.allthreads.averagemessagesendtime.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.averagemessagesendtime.count"));
        metrics.add(new Metric("vds.visitor.allthreads.averageprocessingtime.max"));
        metrics.add(new Metric("vds.visitor.allthreads.averageprocessingtime.sum"));
        metrics.add(new Metric("vds.visitor.allthreads.averageprocessingtime.count"));

        metrics.add(new Metric("vds.filestor.queuesize.max"));
        metrics.add(new Metric("vds.filestor.queuesize.sum"));
        metrics.add(new Metric("vds.filestor.queuesize.count"));
        metrics.add(new Metric("vds.filestor.averagequeuewait.max"));
        metrics.add(new Metric("vds.filestor.averagequeuewait.sum"));
        metrics.add(new Metric("vds.filestor.averagequeuewait.count"));
        metrics.add(new Metric("vds.filestor.active_operations.size.max"));
        metrics.add(new Metric("vds.filestor.active_operations.size.sum"));
        metrics.add(new Metric("vds.filestor.active_operations.size.count"));
        metrics.add(new Metric("vds.filestor.active_operations.latency.max"));
        metrics.add(new Metric("vds.filestor.active_operations.latency.sum"));
        metrics.add(new Metric("vds.filestor.active_operations.latency.count"));
        metrics.add(new Metric("vds.filestor.throttle_window_size.max"));
        metrics.add(new Metric("vds.filestor.throttle_window_size.sum"));
        metrics.add(new Metric("vds.filestor.throttle_window_size.count"));
        metrics.add(new Metric("vds.filestor.throttle_waiting_threads.max"));
        metrics.add(new Metric("vds.filestor.throttle_waiting_threads.sum"));
        metrics.add(new Metric("vds.filestor.throttle_waiting_threads.count"));
        metrics.add(new Metric("vds.filestor.throttle_active_tokens.max"));
        metrics.add(new Metric("vds.filestor.throttle_active_tokens.sum"));
        metrics.add(new Metric("vds.filestor.throttle_active_tokens.count"));
        metrics.add(new Metric("vds.filestor.allthreads.mergemetadatareadlatency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.mergemetadatareadlatency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.mergemetadatareadlatency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.mergedatareadlatency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.mergedatareadlatency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.mergedatareadlatency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.mergedatawritelatency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.mergedatawritelatency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.mergedatawritelatency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.put_latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.put_latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.put_latency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.remove_latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.remove_latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.remove_latency.count"));
        metrics.add(new Metric("vds.filestor.allstripes.throttled_rpc_direct_dispatches.rate"));
        metrics.add(new Metric("vds.filestor.allstripes.throttled_persistence_thread_polls.rate"));
        metrics.add(new Metric("vds.filestor.allstripes.timeouts_waiting_for_throttle_token.rate"));
        
        metrics.add(new Metric("vds.filestor.allthreads.put.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.put.failed.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.put.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.put.latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.put.latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.put.latency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.put.request_size.max"));
        metrics.add(new Metric("vds.filestor.allthreads.put.request_size.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.put.request_size.count"));
        metrics.add(new Metric("vds.filestor.allthreads.remove.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.remove.failed.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.remove.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.remove.latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.remove.latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.remove.latency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.remove.request_size.max"));
        metrics.add(new Metric("vds.filestor.allthreads.remove.request_size.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.remove.request_size.count"));
        metrics.add(new Metric("vds.filestor.allthreads.get.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.get.failed.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.get.latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.get.latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.get.latency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.get.request_size.max"));
        metrics.add(new Metric("vds.filestor.allthreads.get.request_size.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.get.request_size.count"));
        metrics.add(new Metric("vds.filestor.allthreads.update.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.update.failed.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.update.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.update.latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.update.latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.update.latency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.update.request_size.max"));
        metrics.add(new Metric("vds.filestor.allthreads.update.request_size.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.update.request_size.count"));
        metrics.add(new Metric("vds.filestor.allthreads.createiterator.latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.createiterator.latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.createiterator.latency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.createiterator.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.visit.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.visit.latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.visit.latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.visit.latency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.remove_location.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.remove_location.latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.remove_location.latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.remove_location.latency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.splitbuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.joinbuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.deletebuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.deletebuckets.failed.rate"));
        metrics.add(new Metric("vds.filestor.allthreads.deletebuckets.latency.max"));
        metrics.add(new Metric("vds.filestor.allthreads.deletebuckets.latency.sum"));
        metrics.add(new Metric("vds.filestor.allthreads.deletebuckets.latency.count"));
        metrics.add(new Metric("vds.filestor.allthreads.setbucketstates.count.rate"));
        return metrics;
    }
    private static Set<Metric> getDistributorMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();
        metrics.add(new Metric("vds.idealstate.buckets_rechecking.average"));
        metrics.add(new Metric("vds.idealstate.idealstate_diff.average"));
        metrics.add(new Metric("vds.idealstate.buckets_toofewcopies.average"));
        metrics.add(new Metric("vds.idealstate.buckets_toomanycopies.average"));
        metrics.add(new Metric("vds.idealstate.buckets.average"));
        metrics.add(new Metric("vds.idealstate.buckets_notrusted.average"));
        metrics.add(new Metric("vds.idealstate.bucket_replicas_moving_out.average"));
        metrics.add(new Metric("vds.idealstate.bucket_replicas_copying_out.average"));
        metrics.add(new Metric("vds.idealstate.bucket_replicas_copying_in.average"));
        metrics.add(new Metric("vds.idealstate.bucket_replicas_syncing.average"));
        metrics.add(new Metric("vds.idealstate.max_observed_time_since_last_gc_sec.average"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.done_ok.rate"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.done_failed.rate"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.pending.average"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.done_ok.rate"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.done_failed.rate"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.pending.average"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.blocked.rate"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.throttled.rate"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.source_only_copy_changed.rate"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.source_only_copy_delete_blocked.rate"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.source_only_copy_delete_failed.rate"));
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

        metrics.add(new Metric("vds.distributor.puts.latency.max"));
        metrics.add(new Metric("vds.distributor.puts.latency.sum"));
        metrics.add(new Metric("vds.distributor.puts.latency.count"));
        metrics.add(new Metric("vds.distributor.puts.ok.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.notfound.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.concurrent_mutations.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.notconnected.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.notready.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.wrongdistributor.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.safe_time_not_reached.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.storagefailure.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.timeout.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.busy.rate"));
        metrics.add(new Metric("vds.distributor.puts.failures.inconsistent_bucket.rate"));
        metrics.add(new Metric("vds.distributor.removes.latency.max"));
        metrics.add(new Metric("vds.distributor.removes.latency.sum"));
        metrics.add(new Metric("vds.distributor.removes.latency.count"));
        metrics.add(new Metric("vds.distributor.removes.ok.rate"));
        metrics.add(new Metric("vds.distributor.removes.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.removes.failures.notfound.rate"));
        metrics.add(new Metric("vds.distributor.removes.failures.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.distributor.removes.failures.concurrent_mutations.rate"));
        metrics.add(new Metric("vds.distributor.updates.latency.max"));
        metrics.add(new Metric("vds.distributor.updates.latency.sum"));
        metrics.add(new Metric("vds.distributor.updates.latency.count"));
        metrics.add(new Metric("vds.distributor.updates.ok.rate"));
        metrics.add(new Metric("vds.distributor.updates.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.updates.failures.notfound.rate"));
        metrics.add(new Metric("vds.distributor.updates.failures.test_and_set_failed.rate"));
        metrics.add(new Metric("vds.distributor.updates.failures.concurrent_mutations.rate"));
        metrics.add(new Metric("vds.distributor.updates.diverging_timestamp_updates.rate"));
        metrics.add(new Metric("vds.distributor.removelocations.ok.rate"));
        metrics.add(new Metric("vds.distributor.removelocations.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.gets.latency.max"));
        metrics.add(new Metric("vds.distributor.gets.latency.sum"));
        metrics.add(new Metric("vds.distributor.gets.latency.count"));
        metrics.add(new Metric("vds.distributor.gets.ok.rate"));
        metrics.add(new Metric("vds.distributor.gets.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.gets.failures.notfound.rate"));
        metrics.add(new Metric("vds.distributor.visitor.latency.max"));
        metrics.add(new Metric("vds.distributor.visitor.latency.sum"));
        metrics.add(new Metric("vds.distributor.visitor.latency.count"));
        metrics.add(new Metric("vds.distributor.visitor.ok.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.notready.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.notconnected.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.wrongdistributor.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.safe_time_not_reached.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.storagefailure.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.timeout.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.busy.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.inconsistent_bucket.rate"));
        metrics.add(new Metric("vds.distributor.visitor.failures.notfound.rate"));

        metrics.add(new Metric("vds.distributor.docsstored.average"));
        metrics.add(new Metric("vds.distributor.bytesstored.average"));

        metrics.add(new Metric("vds.bouncer.clock_skew_aborts.count"));

        metrics.add(new Metric("vds.mergethrottler.averagequeuewaitingtime.max"));
        metrics.add(new Metric("vds.mergethrottler.averagequeuewaitingtime.sum"));
        metrics.add(new Metric("vds.mergethrottler.averagequeuewaitingtime.count"));
        metrics.add(new Metric("vds.mergethrottler.queuesize.max"));
        metrics.add(new Metric("vds.mergethrottler.queuesize.sum"));
        metrics.add(new Metric("vds.mergethrottler.queuesize.count"));
        metrics.add(new Metric("vds.mergethrottler.active_window_size.max"));
        metrics.add(new Metric("vds.mergethrottler.active_window_size.sum"));
        metrics.add(new Metric("vds.mergethrottler.active_window_size.count"));
        metrics.add(new Metric("vds.mergethrottler.bounced_due_to_back_pressure.rate"));
        metrics.add(new Metric("vds.mergethrottler.locallyexecutedmerges.ok.rate"));
        metrics.add(new Metric("vds.mergethrottler.mergechains.ok.rate"));
        metrics.add(new Metric("vds.mergethrottler.mergechains.failures.busy.rate"));
        metrics.add(new Metric("vds.mergethrottler.mergechains.failures.total.rate"));
        return metrics;
    }

    private static void addMetric(Set<Metric> metrics, String metricName, Iterable<String> aggregateSuffices) {
        for (String suffix : aggregateSuffices) {
            metrics.add(new Metric(metricName + "." + suffix));
        }
    }

}
