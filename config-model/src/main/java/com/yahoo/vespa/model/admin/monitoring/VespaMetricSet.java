// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import com.yahoo.metrics.ContainerMetrics;
import com.yahoo.metrics.Suffix;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.yahoo.metrics.Suffix.average;
import static com.yahoo.metrics.Suffix.count;
import static com.yahoo.metrics.Suffix.last;
import static com.yahoo.metrics.Suffix.max;
import static com.yahoo.metrics.Suffix.min;
import static com.yahoo.metrics.Suffix.sum;
import static com.yahoo.metrics.Suffix.rate;
import static com.yahoo.metrics.Suffix.ninety_five_percentile;
import static com.yahoo.metrics.Suffix.ninety_nine_percentile;
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

        addMetric(metrics, "sentinel.restarts.count");
        addMetric(metrics, "sentinel.totalRestarts.last");
        addMetric(metrics, "sentinel.uptime.last");

        addMetric(metrics, "sentinel.running.count");
        addMetric(metrics, "sentinel.running.last");

        return metrics;
    }

    private static Set<Metric> getOtherMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, "slobrok.heartbeats.failed.count");
        addMetric(metrics, "slobrok.missing.consensus.count");

        addMetric(metrics, "logd.processed.lines.count");
        addMetric(metrics, "worker.connections.max");
        addMetric(metrics, "endpoint.certificate.expiry.seconds");

        // Java (JRT) TLS metrics
        addMetric(metrics, "jrt.transport.tls-certificate-verification-failures");
        addMetric(metrics, "jrt.transport.peer-authorization-failures");
        addMetric(metrics, "jrt.transport.server.tls-connections-established");
        addMetric(metrics, "jrt.transport.client.tls-connections-established");
        addMetric(metrics, "jrt.transport.server.unencrypted-connections-established");
        addMetric(metrics, "jrt.transport.client.unencrypted-connections-established");

        // C++ TLS metrics
        addMetric(metrics, "vds.server.network.tls-handshakes-failed");
        addMetric(metrics, "vds.server.network.peer-authorization-failures");
        addMetric(metrics, "vds.server.network.client.tls-connections-established");
        addMetric(metrics, "vds.server.network.server.tls-connections-established");
        addMetric(metrics, "vds.server.network.client.insecure-connections-established");
        addMetric(metrics, "vds.server.network.server.insecure-connections-established");
        addMetric(metrics, "vds.server.network.tls-connections-broken");
        addMetric(metrics, "vds.server.network.failed-tls-config-reloads");

        // C++ Fnet metrics
        addMetric(metrics, "vds.server.fnet.num-connections");

        // Node certificate
        addMetric(metrics, "node-certificate.expiry.seconds");

        return metrics;
    }

    private static Set<Metric> getConfigServerMetrics() {
        Set<Metric> metrics =new LinkedHashSet<>();

        addMetric(metrics, "configserver.requests.count");
        addMetric(metrics, "configserver.failedRequests.count");
        addMetric(metrics, "configserver.latency.max");
        addMetric(metrics, "configserver.latency.sum");
        addMetric(metrics, "configserver.latency.count");
        addMetric(metrics, "configserver.cacheConfigElems.last");
        addMetric(metrics, "configserver.cacheChecksumElems.last");
        addMetric(metrics, "configserver.hosts.last");
        addMetric(metrics, "configserver.delayedResponses.count");
        addMetric(metrics, "configserver.sessionChangeErrors.count");

        addMetric(metrics, "configserver.zkZNodes.last");
        addMetric(metrics, "configserver.zkAvgLatency.last");
        addMetric(metrics, "configserver.zkMaxLatency.last");
        addMetric(metrics, "configserver.zkConnections.last");
        addMetric(metrics, "configserver.zkOutstandingRequests.last");

        return metrics;
    }

    private static Set<Metric> getContainerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ContainerMetrics.HANDLED_REQUESTS.count());
        addMetric(metrics, ContainerMetrics.HANDLED_LATENCY, EnumSet.of(sum, count, max));
        
        addMetric(metrics, ContainerMetrics.SERVER_NUM_OPEN_CONNECTIONS, EnumSet.of(max,last, average));
        addMetric(metrics, ContainerMetrics.SERVER_NUM_CONNECTIONS, EnumSet.of(max,last, average));

        addMetric(metrics, ContainerMetrics.SERVER_BYTES_RECEIVED, EnumSet.of(sum, count));
        addMetric(metrics, ContainerMetrics.SERVER_BYTES_SENT, EnumSet.of(sum, count));

        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_UNHANDLED_EXCEPTIONS, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_CAPACITY, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_SIZE, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_REJECTED_TASKS, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_SIZE, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_MAX_ALLOWED_SIZE, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_ACTIVE_THREADS, EnumSet.of(sum, count, last, min, max));

        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_MAX_THREADS, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_MIN_THREADS, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_RESERVED_THREADS, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_BUSY_THREADS, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_TOTAL_THREADS, EnumSet.of(sum, count, last, min, max));
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_QUEUE_SIZE, EnumSet.of(sum, count, last, min, max));

        addMetric(metrics, ContainerMetrics.HTTPAPI_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.HTTPAPI_PENDING, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.HTTPAPI_NUM_OPERATIONS.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_NUM_UPDATES.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_NUM_REMOVES.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_NUM_PUTS.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_SUCCEEDED.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_FAILED.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_PARSE_ERROR.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_CONDITION_NOT_MET.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_NOT_FOUND.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_FAILED_UNKNOWN.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_FAILED_INSUFFICIENT_STORAGE.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_FAILED_TIMEOUT.rate());

        addMetric(metrics, ContainerMetrics.MEM_HEAP_TOTAL.average());
        addMetric(metrics, ContainerMetrics.MEM_HEAP_FREE.average());
        addMetric(metrics, ContainerMetrics.MEM_HEAP_USED, EnumSet.of(average, max));
        addMetric(metrics, ContainerMetrics.MEM_DIRECT_TOTAL.average());
        addMetric(metrics, ContainerMetrics.MEM_DIRECT_FREE.average());
        addMetric(metrics, ContainerMetrics.MEM_DIRECT_USED, EnumSet.of(average, max));
        addMetric(metrics, ContainerMetrics.MEM_DIRECT_COUNT.max());
        addMetric(metrics, ContainerMetrics.MEM_NATIVE_TOTAL.average());
        addMetric(metrics, ContainerMetrics.MEM_NATIVE_FREE.average());
        addMetric(metrics, ContainerMetrics.MEM_NATIVE_USED.average());
                
        addMetric(metrics, ContainerMetrics.JDISC_MEMORY_MAPPINGS.max());
        addMetric(metrics, ContainerMetrics.JDISC_OPEN_FILE_DESCRIPTORS.max());

        addMetric(metrics, ContainerMetrics.JDISC_GC_COUNT, EnumSet.of(average, max, last));
        addMetric(metrics, ContainerMetrics.JDISC_GC_MS, EnumSet.of(average, max, last));

        addMetric(metrics, ContainerMetrics.JDISC_DEACTIVATED_CONTAINERS.last());
        addMetric(metrics, ContainerMetrics.JDISC_DEACTIVATED_CONTAINERS_WITH_RETAINED_REFS.last());

        addMetric(metrics, ContainerMetrics.JDISC_SINGLETON_IS_ACTIVE.last());
        addMetric(metrics, ContainerMetrics.JDISC_SINGLETON_ACTIVATION_COUNT.last());
        addMetric(metrics, ContainerMetrics.JDISC_SINGLETON_ACTIVATION_FAILURE_COUNT.last());
        addMetric(metrics, ContainerMetrics.JDISC_SINGLETON_ACTIVATION_MILLIS.last());
        addMetric(metrics, ContainerMetrics.JDISC_SINGLETON_DEACTIVATION_COUNT.last());
        addMetric(metrics, ContainerMetrics.JDISC_SINGLETON_DEACTIVATION_FAILURE_COUNT.last());
        addMetric(metrics, ContainerMetrics.JDISC_SINGLETON_DEACTIVATION_MILLIS.last());

        addMetric(metrics, ContainerMetrics.ATHENZ_TENANT_CERT_EXPIRY_SECONDS.last());
        addMetric(metrics, ContainerMetrics.CONTAINER_IAM_ROLE_EXPIRY_SECONDS.baseName());

        addMetric(metrics, ContainerMetrics.HTTP_STATUS_1XX.rate());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_2XX.rate());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_3XX.rate());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_4XX.rate());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_5XX.rate());

        addMetric(metrics, ContainerMetrics.JDISC_HTTP_REQUEST_PREMATURELY_CLOSED.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_REQUEST_REQUESTS_PER_CONNECTION, EnumSet.of(sum, count, min, max, average));
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_REQUEST_URI_LENGTH, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_REQUEST_CONTENT_SIZE, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_REQUESTS, EnumSet.of(rate, count));

        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_EXPIRED_CLIENT_CERT.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INVALID_CLIENT_CERT.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CHIFERS.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_CONNECTION_CLOSED.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_UNKNOWN.rate());

        addMetric(metrics, ContainerMetrics.JDISC_HTTP_FILTER_RULE_BLOCKED_REQUESTS.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_FILTER_RULE_ALLOWED_REQUESTS.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_FILTERING_REQUEST_HANDLED.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_FILTERING_REQUEST_UNHANDLED.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_FILTERING_RESPONSE_HANDLED.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_FILTERING_RESPONSE_UNHANDLED.rate());

        addMetric(metrics, ContainerMetrics.JDISC_HTTP_HANDLER_UNHANDLED_EXCEPTIONS.rate());

        addMetric(metrics, ContainerMetrics.JDISC_APPLICATION_FAILED_COMPONENT_GRAPHS.rate());

        addMetric(metrics, ContainerMetrics.JDISC_JVM.last());
        
        // Deprecated metrics. TODO: Remove on Vespa 9.
        addMetric(metrics, ContainerMetrics.SERVER_REJECTED_REQUESTS, EnumSet.of(rate, count));             // TODO: Remove on Vespa 9. Use jdisc.thread_pool.rejected_tasks.
        addMetric(metrics, ContainerMetrics.SERVER_THREAD_POOL_SIZE, EnumSet.of(max, last));                // TODO: Remove on Vespa 9. Use jdisc.thread_pool.rejected_tasks.
        addMetric(metrics, ContainerMetrics.SERVER_ACTIVE_THREADS, EnumSet.of(min, max, sum, count, last)); // TODO: Remove on Vespa 9. Use jdisc.thread_pool.rejected_tasks.

        return metrics;
    }

    private static Set<Metric> getClusterControllerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_DOWN_COUNT.last());
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_INITIALIZING_COUNT.last());
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_MAINTENANCE_COUNT.last());
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_RETIRED_COUNT.last());
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_STOPPING_COUNT.last());
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_UP_COUNT.last());
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_CLUSTER_STATE_CHANGE_COUNT.baseName());
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_BUSY_TICK_TIME_MS, EnumSet.of(last, max, sum, count));
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_IDLE_TICK_TIME_MS, EnumSet.of(last, max, sum, count));
        
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_WORK_MS, EnumSet.of(last, sum, count));
        
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_IS_MASTER.last());
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_REMOTE_TASK_QUEUE_SIZE.last());
        // TODO(hakonhall): Update this name once persistent "count" metrics has been implemented.
        // DO NOT RELY ON THIS METRIC YET.
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_NODE_EVENT_COUNT.baseName());
        
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_RESOURCE_USAGE_NODES_ABOVE_LIMIT, EnumSet.of(last, max));
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_RESOURCE_USAGE_MAX_MEMORY_UTILIZATION, EnumSet.of(last, max));
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_RESOURCE_USAGE_MAX_DISK_UTILIZATION, EnumSet.of(last, max));
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_RESOURCE_USAGE_MEMORY_LIMIT.last());
        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_RESOURCE_USAGE_DISK_LIMIT.last());

        addMetric(metrics, ContainerMetrics.CLUSTER_CONTROLLER_REINDEXING_PROGRESS.last());
        
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

        addMetric(metrics, ContainerMetrics.PEAK_QPS.max());
        addMetric(metrics, ContainerMetrics.SEARCH_CONNECTIONS, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.FEED_LATENCY, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.FEED_HTTP_REQUESTS, EnumSet.of(count, rate));
        addMetric(metrics, ContainerMetrics.QUERIES.rate());
        addMetric(metrics, ContainerMetrics.QUERY_CONTAINER_LATENCY, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.QUERY_LATENCY, EnumSet.of(sum, count, max, ninety_five_percentile, ninety_nine_percentile));
        addMetric(metrics, ContainerMetrics.QUERY_TIMEOUT, EnumSet.of(sum, count, max, min, ninety_five_percentile, ninety_nine_percentile));
        addMetric(metrics, ContainerMetrics.FAILED_QUERIES.rate());
        addMetric(metrics, ContainerMetrics.DEGRADED_QUERIES.rate());
        addMetric(metrics, ContainerMetrics.HITS_PER_QUERY, EnumSet.of(sum, count, max, ninety_five_percentile, ninety_nine_percentile));
        addMetric(metrics, ContainerMetrics.SEARCH_CONNECTIONS, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.QUERY_HIT_OFFSET, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.DOCUMENTS_COVERED.count());
        addMetric(metrics, ContainerMetrics.DOCUMENTS_TOTAL.count());
        addMetric(metrics, ContainerMetrics.DOCUMENTS_TARGET_TOTAL.count());
        addMetric(metrics, ContainerMetrics.JDISC_RENDER_LATENCY, EnumSet.of(min, max, count, sum, last, average));
        addMetric(metrics, ContainerMetrics.QUERY_ITEM_COUNT, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.TOTAL_HITS_PER_QUERY, EnumSet.of(sum, count, max, ninety_five_percentile, ninety_nine_percentile));
        addMetric(metrics, ContainerMetrics.EMPTY_RESULTS.rate());
        addMetric(metrics, ContainerMetrics.REQUESTS_OVER_QUOTA, EnumSet.of(rate, count));

        addMetric(metrics, ContainerMetrics.RELEVANCE_AT_1, EnumSet.of(sum, count));
        addMetric(metrics, ContainerMetrics.RELEVANCE_AT_3, EnumSet.of(sum, count));
        addMetric(metrics, ContainerMetrics.RELEVANCE_AT_10, EnumSet.of(sum, count));
                
        // Errors from search container
        addMetric(metrics, ContainerMetrics.ERROR_TIMEOUT.rate());
        addMetric(metrics, ContainerMetrics.ERROR_BACKENDS_OOS.rate());
        addMetric(metrics, ContainerMetrics.ERROR_PLUGIN_FAILURE.rate());
        addMetric(metrics, ContainerMetrics.ERROR_BACKEND_COMMUNICATION_ERROR.rate());
        addMetric(metrics, ContainerMetrics.ERROR_EMPTY_DOCUMENT_SUMMARIES.rate());
        addMetric(metrics, ContainerMetrics.ERROR_INVALID_QUERY_PARAMETER.rate());
        addMetric(metrics, ContainerMetrics.ERROR_INTERNAL_SERVER_ERROR.rate());
        addMetric(metrics, ContainerMetrics.ERROR_MISCONFIGURED_SERVER.rate());
        addMetric(metrics, ContainerMetrics.ERROR_INVALID_QUERY_TRANSFORMATION.rate());
        addMetric(metrics, ContainerMetrics.ERROR_RESULTS_WITH_ERRORS.rate());
        addMetric(metrics, ContainerMetrics.ERROR_UNSPECIFIED.rate());
        addMetric(metrics, ContainerMetrics.ERROR_UNHANDLED_EXCEPTION.rate());

        return metrics;
    }

    private static void addSearchNodeExecutorMetrics(Set<Metric> metrics, String prefix) {
        addMetric(metrics, prefix + ".queuesize.max");
        addMetric(metrics, prefix + ".queuesize.sum");
        addMetric(metrics, prefix + ".queuesize.count");
        addMetric(metrics, prefix + ".accepted.rate");
        addMetric(metrics, prefix + ".wakeups.rate");
        addMetric(metrics, prefix + ".utilization.max");
        addMetric(metrics, prefix + ".utilization.sum");
        addMetric(metrics, prefix + ".utilization.count");
    }

    private static Set<Metric> getSearchNodeMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, "content.proton.documentdb.documents.total.last");
        addMetric(metrics, "content.proton.documentdb.documents.ready.last");
        addMetric(metrics, "content.proton.documentdb.documents.active.last");
        addMetric(metrics,"content.proton.documentdb.documents.removed.last");

        addMetric(metrics, "content.proton.documentdb.index.docs_in_memory.last");
        addMetric(metrics, "content.proton.documentdb.disk_usage.last");
        addMetric(metrics,"content.proton.documentdb.memory_usage.allocated_bytes.max");
        addMetric(metrics, "content.proton.documentdb.heart_beat_age.last");
        addMetric(metrics, "content.proton.transport.query.count.rate");
        addMetric(metrics, "content.proton.docsum.docs.rate");
        addMetric(metrics, "content.proton.docsum.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.transport.query.latency", List.of("max", "sum", "count"));

        // Search protocol
        addMetric(metrics, "content.proton.search_protocol.query.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.search_protocol.query.request_size", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.search_protocol.query.reply_size", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.search_protocol.docsum.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.search_protocol.docsum.request_size", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.search_protocol.docsum.reply_size", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.search_protocol.docsum.requested_documents.count");
        
        // Executors shared between all document dbs
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.proton");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.flush");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.match");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.docsum");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.shared");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.warmup");
        addSearchNodeExecutorMetrics(metrics, "content.proton.executor.field_writer");

        // jobs
        addMetric(metrics, "content.proton.documentdb.job.total.average");
        addMetric(metrics, "content.proton.documentdb.job.attribute_flush.average");
        addMetric(metrics, "content.proton.documentdb.job.memory_index_flush.average");
        addMetric(metrics, "content.proton.documentdb.job.disk_index_fusion.average");
        addMetric(metrics, "content.proton.documentdb.job.document_store_flush.average");
        addMetric(metrics, "content.proton.documentdb.job.document_store_compact.average");
        addMetric(metrics, "content.proton.documentdb.job.bucket_move.average");
        addMetric(metrics, "content.proton.documentdb.job.lid_space_compact.average");
        addMetric(metrics, "content.proton.documentdb.job.removed_documents_prune.average");

        // Threading service (per document db)
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.master");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.index");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.summary");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.index_field_inverter");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.index_field_writer");
        addSearchNodeExecutorMetrics(metrics, "content.proton.documentdb.threading_service.attribute_field_writer");

        // lid space
        addMetric(metrics, "content.proton.documentdb.ready.lid_space.lid_bloat_factor.average");
        addMetric(metrics, "content.proton.documentdb.notready.lid_space.lid_bloat_factor.average");
        addMetric(metrics, "content.proton.documentdb.removed.lid_space.lid_bloat_factor.average");
        addMetric(metrics, "content.proton.documentdb.ready.lid_space.lid_fragmentation_factor.average");
        addMetric(metrics, "content.proton.documentdb.notready.lid_space.lid_fragmentation_factor.average");
        addMetric(metrics, "content.proton.documentdb.removed.lid_space.lid_fragmentation_factor.average");
        addMetric(metrics, "content.proton.documentdb.ready.lid_space.lid_limit.last");
        addMetric(metrics, "content.proton.documentdb.notready.lid_space.lid_limit.last");
        addMetric(metrics, "content.proton.documentdb.removed.lid_space.lid_limit.last");
        addMetric(metrics, "content.proton.documentdb.ready.lid_space.highest_used_lid.last");
        addMetric(metrics, "content.proton.documentdb.notready.lid_space.highest_used_lid.last");
        addMetric(metrics, "content.proton.documentdb.removed.lid_space.highest_used_lid.last");
        addMetric(metrics, "content.proton.documentdb.ready.lid_space.used_lids.last");
        addMetric(metrics, "content.proton.documentdb.notready.lid_space.used_lids.last");
        addMetric(metrics, "content.proton.documentdb.removed.lid_space.used_lids.last");

        // bucket move
        addMetric(metrics, "content.proton.documentdb.bucket_move.buckets_pending.last");

        // resource usage
        addMetric(metrics, "content.proton.resource_usage.disk.average");
        addMetric(metrics, "content.proton.resource_usage.disk_usage.total.max");
        addMetric(metrics, "content.proton.resource_usage.disk_usage.total_utilization.max");
        addMetric(metrics, "content.proton.resource_usage.disk_usage.transient.max");
        addMetric(metrics, "content.proton.resource_usage.memory.average");
        addMetric(metrics, "content.proton.resource_usage.memory_usage.total.max");
        addMetric(metrics, "content.proton.resource_usage.memory_usage.total_utilization.max");
        addMetric(metrics, "content.proton.resource_usage.memory_usage.transient.max");
        addMetric(metrics, "content.proton.resource_usage.memory_mappings.max");
        addMetric(metrics, "content.proton.resource_usage.open_file_descriptors.max");
        addMetric(metrics, "content.proton.resource_usage.feeding_blocked.max");
        addMetric(metrics, "content.proton.resource_usage.malloc_arena.max");
        addMetric(metrics, "content.proton.documentdb.attribute.resource_usage.address_space.max");
        addMetric(metrics, "content.proton.documentdb.attribute.resource_usage.feeding_blocked.max");

        // CPU util
        addMetric(metrics, "content.proton.resource_usage.cpu_util.setup", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.resource_usage.cpu_util.read", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.resource_usage.cpu_util.write", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.resource_usage.cpu_util.compact", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.resource_usage.cpu_util.other", List.of("max", "sum", "count"));

        // transaction log
        addMetric(metrics, "content.proton.transactionlog.entries.average");
        addMetric(metrics, "content.proton.transactionlog.disk_usage.average");
        addMetric(metrics, "content.proton.transactionlog.replay_time.last");

        // document store
        addMetric(metrics, "content.proton.documentdb.ready.document_store.disk_usage.average");
        addMetric(metrics, "content.proton.documentdb.ready.document_store.disk_bloat.average");
        addMetric(metrics, "content.proton.documentdb.ready.document_store.max_bucket_spread.average");
        addMetric(metrics, "content.proton.documentdb.ready.document_store.memory_usage.allocated_bytes.average");
        addMetric(metrics, "content.proton.documentdb.ready.document_store.memory_usage.used_bytes.average");
        addMetric(metrics, "content.proton.documentdb.ready.document_store.memory_usage.dead_bytes.average");
        addMetric(metrics, "content.proton.documentdb.ready.document_store.memory_usage.onhold_bytes.average");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.disk_usage.average");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.disk_bloat.average");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.max_bucket_spread.average");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.memory_usage.allocated_bytes.average");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.memory_usage.used_bytes.average");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.memory_usage.dead_bytes.average");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.memory_usage.onhold_bytes.average");
        addMetric(metrics, "content.proton.documentdb.removed.document_store.disk_usage.average");
        addMetric(metrics, "content.proton.documentdb.removed.document_store.disk_bloat.average");
        addMetric(metrics, "content.proton.documentdb.removed.document_store.max_bucket_spread.average");
        addMetric(metrics, "content.proton.documentdb.removed.document_store.memory_usage.allocated_bytes.average");
        addMetric(metrics, "content.proton.documentdb.removed.document_store.memory_usage.used_bytes.average");
        addMetric(metrics, "content.proton.documentdb.removed.document_store.memory_usage.dead_bytes.average");
        addMetric(metrics, "content.proton.documentdb.removed.document_store.memory_usage.onhold_bytes.average");

        // document store cache
        addMetric(metrics, "content.proton.documentdb.ready.document_store.cache.memory_usage.average", werwerew );
        addMetric(metrics, "content.proton.documentdb.ready.document_store.cache.hit_rate.average");
        addMetric(metrics, "content.proton.documentdb.ready.document_store.cache.lookups.rate");
        addMetric(metrics, "content.proton.documentdb.ready.document_store.cache.invalidations.rate");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.cache.memory_usage.average");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.cache.hit_rate.average");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.cache.lookups.rate");
        addMetric(metrics, "content.proton.documentdb.notready.document_store.cache.invalidations.rate");

        // attribute
        addMetric(metrics, "content.proton.documentdb.ready.attribute.memory_usage.allocated_bytes.average");
        addMetric(metrics, "content.proton.documentdb.ready.attribute.memory_usage.used_bytes.average");
        addMetric(metrics, "content.proton.documentdb.ready.attribute.memory_usage.dead_bytes.average");
        addMetric(metrics, "content.proton.documentdb.ready.attribute.memory_usage.onhold_bytes.average");
        addMetric(metrics, "content.proton.documentdb.notready.attribute.memory_usage.allocated_bytes.average");
        addMetric(metrics, "content.proton.documentdb.notready.attribute.memory_usage.used_bytes.average");
        addMetric(metrics, "content.proton.documentdb.notready.attribute.memory_usage.dead_bytes.average");
        addMetric(metrics, "content.proton.documentdb.notready.attribute.memory_usage.onhold_bytes.average");

        // index
        addMetric(metrics, "content.proton.documentdb.index.memory_usage.allocated_bytes.average");
        addMetric(metrics, "content.proton.documentdb.index.memory_usage.used_bytes.average");
        addMetric(metrics, "content.proton.documentdb.index.memory_usage.dead_bytes.average");
        addMetric(metrics, "content.proton.documentdb.index.memory_usage.onhold_bytes.average");

        // matching
        addMetric(metrics, "content.proton.documentdb.matching.queries.rate");
        addMetric(metrics, "content.proton.documentdb.matching.soft_doomed_queries.rate");
        addMetric(metrics, "content.proton.documentdb.matching.query_latency", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.documentdb.matching.query_setup_time", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.documentdb.matching.docs_matched", List.of("rate", "count"));
        addMetric(metrics, "content.proton.documentdb.matching.rank_profile.queries.rate");
        addMetric(metrics, "content.proton.documentdb.matching.rank_profile.soft_doomed_queries.rate");
        addMetric(metrics, "content.proton.documentdb.matching.rank_profile.soft_doom_factor", List.of("min", "max", "sum", "count"));
        addMetric(metrics, "content.proton.documentdb.matching.rank_profile.query_latency", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.documentdb.matching.rank_profile.query_setup_time", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.documentdb.matching.rank_profile.grouping_time", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.documentdb.matching.rank_profile.rerank_time", List.of("max", "sum", "count"));
        addMetric(metrics, "content.proton.documentdb.matching.rank_profile.docs_matched", List.of("rate", "count"));
        addMetric(metrics, "content.proton.documentdb.matching.rank_profile.limited_queries.rate");

        // feeding
        addMetric(metrics, "content.proton.documentdb.feeding.commit.operations", List.of("max", "sum", "count", "rate"));
        addMetric(metrics, "content.proton.documentdb.feeding.commit.latency", List.of("max", "sum", "count"));

        return metrics;
    }

    private static Set<Metric> getStorageMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        // TODO: For the purpose of this file and likely elsewhere, all but the last aggregate specifier,
        // TODO: such as 'average' and 'sum' in the metric names below are just confusing and can be mentally
        // TODO: disregarded when considering metric names. Consider cleaning up for Vespa 9.
        addMetric(metrics, "vds.datastored.alldisks.buckets.average");
        addMetric(metrics, "vds.datastored.alldisks.docs.average");
        addMetric(metrics, "vds.datastored.alldisks.bytes.average");
        addMetric(metrics, "vds.visitor.allthreads.averagevisitorlifetime", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.visitor.allthreads.averagequeuewait", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.visitor.allthreads.queuesize", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.visitor.allthreads.completed.rate");
        addMetric(metrics, "vds.visitor.allthreads.created.rate");
        addMetric(metrics, "vds.visitor.allthreads.failed.rate");
        addMetric(metrics, "vds.visitor.allthreads.averagemessagesendtime", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.visitor.allthreads.averageprocessingtime", List.of("max", "sum", "count"));

        addMetric(metrics, "vds.filestor.queuesize", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.averagequeuewait", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.active_operations.size", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.active_operations.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.throttle_window_size", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.throttle_waiting_threads", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.throttle_active_tokens", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.mergemetadatareadlatency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.mergedatareadlatency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.mergedatawritelatency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.put_latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.remove_latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allstripes.throttled_rpc_direct_dispatches.rate");
        addMetric(metrics, "vds.filestor.allstripes.throttled_persistence_thread_polls.rate");
        addMetric(metrics, "vds.filestor.allstripes.timeouts_waiting_for_throttle_token.rate");
        
        addMetric(metrics, "vds.filestor.allthreads.put.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.put.failed.rate");
        addMetric(metrics, "vds.filestor.allthreads.put.test_and_set_failed.rate");
        addMetric(metrics, "vds.filestor.allthreads.put.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.put.request_size", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.remove.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.remove.failed.rate");
        addMetric(metrics, "vds.filestor.allthreads.remove.test_and_set_failed.rate");
        addMetric(metrics, "vds.filestor.allthreads.remove.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.remove.request_size", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.get.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.get.failed.rate");
        addMetric(metrics, "vds.filestor.allthreads.get.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.get.request_size", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.update.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.update.failed.rate");
        addMetric(metrics, "vds.filestor.allthreads.update.test_and_set_failed.rate");
        addMetric(metrics, "vds.filestor.allthreads.update.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.update.request_size", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.createiterator.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.createiterator.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.visit.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.visit.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.remove_location.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.remove_location.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.splitbuckets.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.joinbuckets.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.deletebuckets.count.rate");
        addMetric(metrics, "vds.filestor.allthreads.deletebuckets.failed.rate");
        addMetric(metrics, "vds.filestor.allthreads.deletebuckets.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.filestor.allthreads.setbucketstates.count.rate");
        return metrics;
    }
    private static Set<Metric> getDistributorMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();
        addMetric(metrics, "vds.idealstate.buckets_rechecking.average");
        addMetric(metrics, "vds.idealstate.idealstate_diff.average");
        addMetric(metrics, "vds.idealstate.buckets_toofewcopies.average");
        addMetric(metrics, "vds.idealstate.buckets_toomanycopies.average");
        addMetric(metrics, "vds.idealstate.buckets.average");
        addMetric(metrics, "vds.idealstate.buckets_notrusted.average");
        addMetric(metrics, "vds.idealstate.bucket_replicas_moving_out.average");
        addMetric(metrics, "vds.idealstate.bucket_replicas_copying_out.average");
        addMetric(metrics, "vds.idealstate.bucket_replicas_copying_in.average");
        addMetric(metrics, "vds.idealstate.bucket_replicas_syncing.average");
        addMetric(metrics, "vds.idealstate.max_observed_time_since_last_gc_sec.average");
        addMetric(metrics, "vds.idealstate.delete_bucket.done_ok.rate");
        addMetric(metrics, "vds.idealstate.delete_bucket.done_failed.rate");
        addMetric(metrics, "vds.idealstate.delete_bucket.pending.average");
        addMetric(metrics, "vds.idealstate.merge_bucket.done_ok.rate");
        addMetric(metrics, "vds.idealstate.merge_bucket.done_failed.rate");
        addMetric(metrics, "vds.idealstate.merge_bucket.pending.average");
        addMetric(metrics, "vds.idealstate.merge_bucket.blocked.rate");
        addMetric(metrics, "vds.idealstate.merge_bucket.throttled.rate");
        addMetric(metrics, "vds.idealstate.merge_bucket.source_only_copy_changed.rate");
        addMetric(metrics, "vds.idealstate.merge_bucket.source_only_copy_delete_blocked.rate");
        addMetric(metrics, "vds.idealstate.merge_bucket.source_only_copy_delete_failed.rate");
        addMetric(metrics, "vds.idealstate.split_bucket.done_ok.rate");
        addMetric(metrics, "vds.idealstate.split_bucket.done_failed.rate");
        addMetric(metrics, "vds.idealstate.split_bucket.pending.average");
        addMetric(metrics, "vds.idealstate.join_bucket.done_ok.rate");
        addMetric(metrics, "vds.idealstate.join_bucket.done_failed.rate");
        addMetric(metrics, "vds.idealstate.join_bucket.pending.average");
        addMetric(metrics, "vds.idealstate.garbage_collection.done_ok.rate");
        addMetric(metrics, "vds.idealstate.garbage_collection.done_failed.rate");
        addMetric(metrics, "vds.idealstate.garbage_collection.pending.average");
        addMetric(metrics, "vds.idealstate.garbage_collection.documents_removed", List.of("count", "rate"));

        addMetric(metrics, "vds.distributor.puts.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.distributor.puts.ok.rate");
        addMetric(metrics, "vds.distributor.puts.failures.total.rate");
        addMetric(metrics, "vds.distributor.puts.failures.notfound.rate");
        addMetric(metrics, "vds.distributor.puts.failures.test_and_set_failed.rate");
        addMetric(metrics, "vds.distributor.puts.failures.concurrent_mutations.rate");
        addMetric(metrics, "vds.distributor.puts.failures.notconnected.rate");
        addMetric(metrics, "vds.distributor.puts.failures.notready.rate");
        addMetric(metrics, "vds.distributor.puts.failures.wrongdistributor.rate");
        addMetric(metrics, "vds.distributor.puts.failures.safe_time_not_reached.rate");
        addMetric(metrics, "vds.distributor.puts.failures.storagefailure.rate");
        addMetric(metrics, "vds.distributor.puts.failures.timeout.rate");
        addMetric(metrics, "vds.distributor.puts.failures.busy.rate");
        addMetric(metrics, "vds.distributor.puts.failures.inconsistent_bucket.rate");
        addMetric(metrics, "vds.distributor.removes.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.distributor.removes.ok.rate");
        addMetric(metrics, "vds.distributor.removes.failures.total.rate");
        addMetric(metrics, "vds.distributor.removes.failures.notfound.rate");
        addMetric(metrics, "vds.distributor.removes.failures.test_and_set_failed.rate");
        addMetric(metrics, "vds.distributor.removes.failures.concurrent_mutations.rate");
        addMetric(metrics, "vds.distributor.updates.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.distributor.updates.ok.rate");
        addMetric(metrics, "vds.distributor.updates.failures.total.rate");
        addMetric(metrics, "vds.distributor.updates.failures.notfound.rate");
        addMetric(metrics, "vds.distributor.updates.failures.test_and_set_failed.rate");
        addMetric(metrics, "vds.distributor.updates.failures.concurrent_mutations.rate");
        addMetric(metrics, "vds.distributor.updates.diverging_timestamp_updates.rate");
        addMetric(metrics, "vds.distributor.removelocations.ok.rate");
        addMetric(metrics, "vds.distributor.removelocations.failures.total.rate");
        addMetric(metrics, "vds.distributor.gets.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.distributor.gets.ok.rate");
        addMetric(metrics, "vds.distributor.gets.failures.total.rate");
        addMetric(metrics, "vds.distributor.gets.failures.notfound.rate");
        addMetric(metrics, "vds.distributor.visitor.latency", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.distributor.visitor.ok.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.total.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.notready.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.notconnected.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.wrongdistributor.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.safe_time_not_reached.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.storagefailure.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.timeout.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.busy.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.inconsistent_bucket.rate");
        addMetric(metrics, "vds.distributor.visitor.failures.notfound.rate");

        addMetric(metrics, "vds.distributor.docsstored.average");
        addMetric(metrics, "vds.distributor.bytesstored.average");

        addMetric(metrics, "vds.bouncer.clock_skew_aborts.count");

        addMetric(metrics, "vds.mergethrottler.averagequeuewaitingtime", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.mergethrottler.queuesize", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.mergethrottler.active_window_size", List.of("max", "sum", "count"));
        addMetric(metrics, "vds.mergethrottler.bounced_due_to_back_pressure.rate");
        addMetric(metrics, "vds.mergethrottler.locallyexecutedmerges.ok.rate");
        addMetric(metrics, "vds.mergethrottler.mergechains.ok.rate");
        addMetric(metrics, "vds.mergethrottler.mergechains.failures.busy.rate");
        addMetric(metrics, "vds.mergethrottler.mergechains.failures.total.rate");
        return metrics;
    }

    private static void addMetric(Set<Metric> metrics, String nameWithSuffix) {
        metrics.add(new Metric(nameWithSuffix));
    }

    private static void addMetric(Set<Metric> metrics, ContainerMetrics metric, EnumSet<Suffix> suffixes) {
        suffixes.forEach(suffix -> metrics.add(new Metric(metric.baseName() + "." + suffix.suffix())));
    }

    private static void addMetric(Set<Metric> metrics, String metricName, Iterable<String> aggregateSuffices) {
        for (String suffix : aggregateSuffices) {
            metrics.add(new Metric(metricName + "." + suffix));
        }
    }

}
