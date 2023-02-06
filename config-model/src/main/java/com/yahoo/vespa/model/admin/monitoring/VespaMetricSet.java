// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import com.yahoo.metrics.ContainerMetrics;
import com.yahoo.metrics.SearchNodeMetrics;
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

    private static Set<Metric> getSearchNodeMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_TOTAL.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_READY.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_ACTIVE.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_REMOVED.last());

        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_DOCS_IN_MEMORY.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DISK_USAGE.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MEMORY_USAGE_ALLOCATED_BYTES.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_HEART_BEAT_AGE.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCSUM_DOCS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCSUM_LATENCY, EnumSet.of(max, sum, count));

        // Search protocol
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_REQUEST_SIZE, EnumSet.of(max, sum, average));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_REQUEST_SIZE, EnumSet.of(max, sum, average));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_LATENCY, EnumSet.of(max, sum, average));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REQUEST_SIZE, EnumSet.of(max, sum, average));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REPLY_SIZE, EnumSet.of(max, sum, average));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REQUESTED_DOCUMENTS.count());

        // Executors shared between all document dbs
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_PROTON_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_PROTON_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_PROTON_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_PROTON_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FLUSH_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FLUSH_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FLUSH_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FLUSH_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_MATCH_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_MATCH_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_MATCH_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_MATCH_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_DOCSUM_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_DOCSUM_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_DOCSUM_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_DOCSUM_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_SHARED_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_SHARED_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_SHARED_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_SHARED_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_WARMUP_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_WARMUP_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_WARMUP_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_WARMUP_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FIELD_WRITER_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FIELD_WRITER_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FIELD_WRITER_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FIELD_WRITER_UTILIZATION, EnumSet.of(max, sum, count));

        // jobs
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_JOB_TOTAL.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_JOB_ATTRIBUTE_FLUSH.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_JOB_MEMORY_INDEX_FLUSH.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_JOB_DISK_INDEX_FUSION.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_JOB_DOCUMENT_STORE_FLUSH.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_JOB_DOCUMENT_STORE_COMPACT.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_JOB_BUCKET_MOVE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_JOB_LID_SPACE_COMPACT.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_JOB_REMOVED_DOCUMENTS_PRUNE.average());

        // Threading service (per document db)
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_INVERTER_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_INVERTER_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_INVERTER_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_INVERTER_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_WRITER_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_WRITER_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_WRITER_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_WRITER_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_ATTRIBUTE_FIELD_WRITER_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_ATTRIBUTE_FIELD_WRITER_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_ATTRIBUTE_FIELD_WRITER_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_ATTRIBUTE_FIELD_WRITER_UTILIZATION, EnumSet.of(max, sum, count));

        // lid space
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_LID_BLOAT_FACTOR.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_LID_FRAGMENTATION_FACTOR.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_LID_LIMIT.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_HIGHEST_USED_LID.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_USED_LIDS.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_LID_BLOAT_FACTOR.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_LID_FRAGMENTATION_FACTOR.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_LID_LIMIT.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_HIGHEST_USED_LID.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_USED_LIDS.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_LID_BLOAT_FACTOR.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_LID_FRAGMENTATION_FACTOR.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_LID_LIMIT.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_HIGHEST_USED_LID.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_USED_LIDS.last());

        // bucket move
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_BUCKET_MOVE_BUCKETS_PENDING.last());

        // resource usage
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_TOTAL.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_TOTAL_UTILIZATION.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_TRANSIENT.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY_USAGE_TOTAL.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY_USAGE_TOTAL_UTILIZATION.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY_USAGE_TRANSIENT.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY_MAPPINGS.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_OPEN_FILE_DESCRIPTORS.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_FEEDING_BLOCKED.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MALLOC_ARENA.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_ATTRIBUTE_RESOURCE_USAGE_ADDRESS_SPACE.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_ATTRIBUTE_RESOURCE_USAGE_FEEDING_BLOCKED.max());

        // CPU util
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_SETUP, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_READ, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_WRITE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_COMPACT, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_OTHER, EnumSet.of(max, sum, count));

        // transaction log
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_TRANSACTIONLOG_ENTRIES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_TRANSACTIONLOG_DISK_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_TRANSACTIONLOG_REPLAY_TIME.last());

        // document store
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_DISK_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_DISK_BLOAT.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MAX_BUCKET_SPREAD.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MEMORY_USAGE_ALLOCATED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MEMORY_USAGE_USED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MEMORY_USAGE_ONHOLD_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_DISK_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_DISK_BLOAT.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MAX_BUCKET_SPREAD.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MEMORY_USAGE_ALLOCATED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MEMORY_USAGE_USED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MEMORY_USAGE_DEAD_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MEMORY_USAGE_ONHOLD_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_DISK_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_DISK_BLOAT.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MAX_BUCKET_SPREAD.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MEMORY_USAGE_ALLOCATED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MEMORY_USAGE_USED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MEMORY_USAGE_DEAD_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MEMORY_USAGE_ONHOLD_BYTES.average());

        // document store cache
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_CACHE_MEMORY_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_CACHE_HIT_RATE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_CACHE_LOOKUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_CACHE_INVALIDATIONS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_CACHE_MEMORY_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_CACHE_HIT_RATE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_CACHE_LOOKUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_CACHE_INVALIDATIONS.rate());

        // attribute
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_ATTRIBUTE_MEMORY_USAGE_ALLOCATED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_ATTRIBUTE_MEMORY_USAGE_USED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_ATTRIBUTE_MEMORY_USAGE_DEAD_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_ATTRIBUTE_MEMORY_USAGE_ONHOLD_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_ATTRIBUTE_MEMORY_USAGE_ALLOCATED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_ATTRIBUTE_MEMORY_USAGE_USED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_ATTRIBUTE_MEMORY_USAGE_DEAD_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_ATTRIBUTE_MEMORY_USAGE_ONHOLD_BYTES.average());

        // index
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_MEMORY_USAGE_ALLOCATED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_MEMORY_USAGE_USED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_MEMORY_USAGE_DEAD_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_MEMORY_USAGE_ONHOLD_BYTES.average());

        // matching
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERIES.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_SOFT_DOOMED_QUERIES.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERY_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERY_SETUP_TIME, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_MATCHED, EnumSet.of(rate, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERIES.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_SOFT_DOOMED_QUERIES.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_SOFT_DOOM_FACTOR, EnumSet.of(min, max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_SETUP_TIME, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_GROUPING_TIME, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_RERANK_TIME, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCS_MATCHED, EnumSet.of(rate, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_LIMITED_QUERIES.rate());

        // feeding
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_FEEDING_COMMIT_OPERATIONS, EnumSet.of(max, sum, count, rate));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_FEEDING_COMMIT_LATENCY, EnumSet.of(max, sum, count));

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

    private static void addMetric(Set<Metric> metrics, SearchNodeMetrics metric, EnumSet<Suffix> suffixes) {
        suffixes.forEach(suffix -> metrics.add(new Metric(metric.baseName() + "." + suffix.suffix())));
    }

    private static void addMetric(Set<Metric> metrics, String metricName, Iterable<String> aggregateSuffices) {
        for (String suffix : aggregateSuffices) {
            metrics.add(new Metric(metricName + "." + suffix));
        }
    }

}
