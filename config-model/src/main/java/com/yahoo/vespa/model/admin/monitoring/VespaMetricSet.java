// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import ai.vespa.metrics.ClusterControllerMetrics;
import ai.vespa.metrics.ConfigServerMetrics;
import ai.vespa.metrics.ContainerMetrics;
import ai.vespa.metrics.DistributorMetrics;
import ai.vespa.metrics.LogdMetrics;
import ai.vespa.metrics.NodeAdminMetrics;
import ai.vespa.metrics.RoutingLayerMetrics;
import ai.vespa.metrics.SearchNodeMetrics;
import ai.vespa.metrics.SentinelMetrics;
import ai.vespa.metrics.SlobrokMetrics;
import ai.vespa.metrics.StorageMetrics;
import ai.vespa.metrics.Suffix;
import ai.vespa.metrics.VespaMetrics;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static ai.vespa.metrics.Suffix.average;
import static ai.vespa.metrics.Suffix.count;
import static ai.vespa.metrics.Suffix.last;
import static ai.vespa.metrics.Suffix.max;
import static ai.vespa.metrics.Suffix.min;
import static ai.vespa.metrics.Suffix.ninety_five_percentile;
import static ai.vespa.metrics.Suffix.ninety_nine_percentile;
import static ai.vespa.metrics.Suffix.rate;
import static ai.vespa.metrics.Suffix.sum;
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

        addMetric(metrics, SentinelMetrics.SENTINEL_RESTARTS.count());
        addMetric(metrics, SentinelMetrics.SENTINEL_TOTAL_RESTARTS.last());
        addMetric(metrics, SentinelMetrics.SENTINEL_UPTIME.last());
        addMetric(metrics, SentinelMetrics.SENTINEL_RUNNING, EnumSet.of(count, last));

        return metrics;
    }

    private static Set<Metric> getOtherMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, SlobrokMetrics.SLOBROK_HEARTBEATS_FAILED.count());
        addMetric(metrics, SlobrokMetrics.SLOBROK_MISSING_CONSENSUS.count());

        addMetric(metrics, LogdMetrics.LOGD_PROCESSED_LINES.count());

        // Java (JRT) TLS metrics
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_TLS_CERTIFICATE_VERIFICATION_FAILURES.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_PEER_AUTHORIZATION_FAILURES.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_SERVER_TLS_CONNECIONTS_ESTABLISHED.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_CLIENT_TLS_CONNECTIONS_ESTABLISHED.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_SERVER_UNENCRYPTED_CONNECTIONS_ESTABLISHED.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_CLIENT_UNENCRYPTED_CONNECTIONS_ESTABLISHED.baseName());

        // C++ TLS metrics
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_TLS_HANDSHAKES_FAILED.count());
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_PEER_AUTHORIZATION_FAILURES.count());
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_CLIENT_TLS_CONNECTIONS_ESTABLISHED.count());
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_SERVER_TLS_CONNECTIONS_ESTABLISHED.count());
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_CLIENT_INSECURE_CONNECTIONS_ESTABLISHED.count());
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_SERVER_INSECURE_CONNECTIONS_ESTABLISHED.count());
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_TLS_CONNECTIONS_BROKEN.count());
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_FAILED_TLS_CONFIG_RELOADS.count());
        // C++ capability metrics
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_RPC_CAPABILITY_CHECKS_FAILED.count());
        addMetric(metrics, StorageMetrics.VDS_SERVER_NETWORK_STATUS_CAPABILITY_CHECKS_FAILED.count());

        // C++ Fnet metrics
        addMetric(metrics, StorageMetrics.VDS_SERVER_FNET_NUM_CONNECTIONS.count());

        // NodeAdmin certificate
        addMetric(metrics, NodeAdminMetrics.ENDPOINT_CERTIFICATE_EXPIRY_SECONDS.baseName());
        addMetric(metrics, NodeAdminMetrics.NODE_CERTIFICATE_EXPIRY_SECONDS.baseName());

        // Routing layer metrics
        addMetric(metrics, RoutingLayerMetrics.WORKER_CONNECTIONS.max()); // Hosted Vespa only (routing layer)

        return metrics;
    }

    private static Set<Metric> getConfigServerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ConfigServerMetrics.REQUESTS.count());
        addMetric(metrics, ConfigServerMetrics.FAILED_REQUESTS.count());
        addMetric(metrics, ConfigServerMetrics.LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, ConfigServerMetrics.CACHE_CONFIG_ELEMS.last());
        addMetric(metrics, ConfigServerMetrics.CACHE_CHECKSUM_ELEMS.last());
        addMetric(metrics, ConfigServerMetrics.HOSTS.last());
        addMetric(metrics, ConfigServerMetrics.DELAYED_RESPONSES.count());
        addMetric(metrics, ConfigServerMetrics.SESSION_CHANGE_ERRORS.count());

        addMetric(metrics, ConfigServerMetrics.ZK_Z_NODES.last());
        addMetric(metrics, ConfigServerMetrics.ZK_AVG_LATENCY.last());
        addMetric(metrics, ConfigServerMetrics.ZK_MAX_LATENCY.last());
        addMetric(metrics, ConfigServerMetrics.ZK_CONNECTIONS.last());
        addMetric(metrics, ConfigServerMetrics.ZK_OUTSTANDING_REQUESTS.last());

        // Node repository metrics
        addMetric(metrics, ConfigServerMetrics.NODES_NON_ACTIVE_FRACTION.last());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_COST.last());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_IDEAL_CPU.last());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_IDEAL_MEMORY.last());
        addMetric(metrics, ConfigServerMetrics.CLUSTER_LOAD_IDEAL_DISK.last());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_REBOOT.max());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_RESTART.max());
        addMetric(metrics, ConfigServerMetrics.RETIRED.max());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_CHANGE_VESPA_VERSION.max());
        addMetric(metrics, ConfigServerMetrics.HAS_WIRE_GUARD_KEY.last());
        addMetric(metrics, ConfigServerMetrics.WANT_TO_DEPROVISION.max());
        addMetric(metrics, ConfigServerMetrics.SUSPENDED.max());
        addMetric(metrics, ConfigServerMetrics.SOME_SERVICES_DOWN.max());
        addMetric(metrics, ConfigServerMetrics.NODE_FAILER_BAD_NODE.last());
        addMetric(metrics, ConfigServerMetrics.LOCK_ATTEMPT_LOCKED_LOAD, EnumSet.of(max,average));

        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_CPU.average());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_MEM.average());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_ALLOCATED_CAPACITY_DISK.average());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_CPU.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_MEM.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_FREE_CAPACITY_DISK.max());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_CPU, EnumSet.of(max,average));
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_DISK, EnumSet.of(max,average));
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_TOTAL_CAPACITY_MEM, EnumSet.of(max,average));
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_DOCKER_SKEW.last());
        addMetric(metrics, ConfigServerMetrics.HOSTED_VESPA_PENDING_REDEPLOYMENTS.last());

        return metrics;
    }

    private static Set<Metric> getContainerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ContainerMetrics.APPLICATION_GENERATION.baseName());

        addMetric(metrics, ContainerMetrics.HANDLED_REQUESTS.count());
        addMetric(metrics, ContainerMetrics.HANDLED_LATENCY, EnumSet.of(sum, count, max));

        addMetric(metrics, ContainerMetrics.SERVER_NUM_OPEN_CONNECTIONS, EnumSet.of(max, last, average)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ContainerMetrics.SERVER_NUM_CONNECTIONS, EnumSet.of(max, last, average)); // TODO: Vespa 9: Remove last

        addMetric(metrics, ContainerMetrics.SERVER_BYTES_RECEIVED, EnumSet.of(sum, count));
        addMetric(metrics, ContainerMetrics.SERVER_BYTES_SENT, EnumSet.of(sum, count));

        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_UNHANDLED_EXCEPTIONS, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove last, min, max
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_CAPACITY, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove sum, count, last, min
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_SIZE, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_REJECTED_TASKS, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove last, min, max
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_SIZE, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove sum, count, last, min
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_MAX_ALLOWED_SIZE, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove sum, count, last, min
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_ACTIVE_THREADS, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove last

        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_MAX_THREADS, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove.
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_MIN_THREADS, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove.
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_RESERVED_THREADS, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove.
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_BUSY_THREADS, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove last, min
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_TOTAL_THREADS, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove sum, count, last, min
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_QUEUE_SIZE, EnumSet.of(sum, count, last, min, max)); // TODO: Vespa 9: Remove sum, count, last, min

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

        addMetric(metrics, ContainerMetrics.JDISC_GC_COUNT, EnumSet.of(average, max, last)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ContainerMetrics.JDISC_GC_MS, EnumSet.of(average, max, last)); // TODO: Vespa 9: Remove last

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
        addMetric(metrics, ContainerMetrics.JDISC_APPLICATION_COMPONENT_GRAPH_CREATION_TIME_MILLIS.last());
        addMetric(metrics, ContainerMetrics.JDISC_APPLICATION_COMPONENT_GRAPH_RECONFIGURATIONS.rate());

        addMetric(metrics, ContainerMetrics.JDISC_JVM.last());

        // Deprecated metrics. TODO: Remove on Vespa 9.
        addMetric(metrics, ContainerMetrics.SERVER_REJECTED_REQUESTS, EnumSet.of(rate, count));             // TODO: Remove on Vespa 9. Use jdisc.thread_pool.rejected_tasks.
        addMetric(metrics, ContainerMetrics.SERVER_THREAD_POOL_SIZE, EnumSet.of(max, last));                // TODO: Remove on Vespa 9. Use jdisc.thread_pool.rejected_tasks.
        addMetric(metrics, ContainerMetrics.SERVER_ACTIVE_THREADS, EnumSet.of(min, max, sum, count, last)); // TODO: Remove on Vespa 9. Use jdisc.thread_pool.rejected_tasks.

        addMetric(metrics, ContainerMetrics.JDISC_TLS_CAPABILITY_CHECKS_SUCCEEDED.rate());
        addMetric(metrics, ContainerMetrics.JDISC_TLS_CAPABILITY_CHECKS_FAILED.rate());

        return metrics;
    }

    private static Set<Metric> getClusterControllerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ClusterControllerMetrics.DOWN_COUNT.last());
        addMetric(metrics, ClusterControllerMetrics.INITIALIZING_COUNT.last());
        addMetric(metrics, ClusterControllerMetrics.MAINTENANCE_COUNT.last());
        addMetric(metrics, ClusterControllerMetrics.RETIRED_COUNT.last());
        addMetric(metrics, ClusterControllerMetrics.STOPPING_COUNT.last());
        addMetric(metrics, ClusterControllerMetrics.UP_COUNT.last());
        addMetric(metrics, ClusterControllerMetrics.CLUSTER_STATE_CHANGE_COUNT.baseName());
        addMetric(metrics, ClusterControllerMetrics.BUSY_TICK_TIME_MS, EnumSet.of(last, max, sum, count)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ClusterControllerMetrics.IDLE_TICK_TIME_MS, EnumSet.of(last, max, sum, count)); // TODO: Vespa 9: Remove last

        addMetric(metrics, ClusterControllerMetrics.WORK_MS, EnumSet.of(last, sum, count)); // TODO: Vespa 9: Remove last

        addMetric(metrics, ClusterControllerMetrics.IS_MASTER.last());
        addMetric(metrics, ClusterControllerMetrics.REMOTE_TASK_QUEUE_SIZE.last());
        // TODO(hakonhall): Update this name once persistent "count" metrics has been implemented.
        // DO NOT RELY ON THIS METRIC YET.
        addMetric(metrics, ClusterControllerMetrics.NODE_EVENT_COUNT.baseName());
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_NODES_ABOVE_LIMIT, EnumSet.of(last, max)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MAX_MEMORY_UTILIZATION, EnumSet.of(last, max)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MAX_DISK_UTILIZATION, EnumSet.of(last, max)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MEMORY_LIMIT.last());
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_DISK_LIMIT.last());
        addMetric(metrics, ClusterControllerMetrics.REINDEXING_PROGRESS.last());

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
        addMetric(metrics, ContainerMetrics.JDISC_RENDER_LATENCY, EnumSet.of(min, max, count, sum, last, average)); // TODO: Vespa 9: Remove last, average
        addMetric(metrics, ContainerMetrics.QUERY_ITEM_COUNT, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.TOTAL_HITS_PER_QUERY, EnumSet.of(sum, count, max, ninety_five_percentile, ninety_nine_percentile));
        addMetric(metrics, ContainerMetrics.EMPTY_RESULTS.rate());
        addMetric(metrics, ContainerMetrics.REQUESTS_OVER_QUOTA, EnumSet.of(rate, count));
        addMetric(metrics, ContainerMetrics.DOCPROC_PROC_TIME, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.DOCPROC_DOCUMENTS, EnumSet.of(sum, count, max, min));

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

        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_CONFIG_GENERATION.last());

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
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_REQUEST_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_REPLY_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_LATENCY, EnumSet.of(max, sum, average));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REQUEST_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REPLY_SIZE, EnumSet.of(max, sum, count));
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

        // TODO - Vespa 9: For the purpose of this file and likely elsewhere, all but the last aggregate specifier,
        // TODO - Vespa 9: such as 'average' and 'sum' in the metric names below are just confusing and can be mentally
        // TODO - Vespa 9: disregarded when considering metric names. Clean up for Vespa 9.
        addMetric(metrics, StorageMetrics.VDS_DATASTORED_ALLDISKS_BUCKETS.average());
        addMetric(metrics, StorageMetrics.VDS_DATASTORED_ALLDISKS_DOCS.average());
        addMetric(metrics, StorageMetrics.VDS_DATASTORED_ALLDISKS_BYTES.average());
        addMetric(metrics, StorageMetrics.VDS_VISITOR_ALLTHREADS_AVERAGEVISITORLIFETIME, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_VISITOR_ALLTHREADS_AVERAGEQUEUEWAIT, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_VISITOR_ALLTHREADS_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_VISITOR_ALLTHREADS_COMPLETED.rate());
        addMetric(metrics, StorageMetrics.VDS_VISITOR_ALLTHREADS_CREATED.rate());
        addMetric(metrics, StorageMetrics.VDS_VISITOR_ALLTHREADS_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_VISITOR_ALLTHREADS_AVERAGEMESSAGESENDTIME, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_VISITOR_ALLTHREADS_AVERAGEPROCESSINGTIME, EnumSet.of(max, sum, count));

        addMetric(metrics, StorageMetrics.VDS_FILESTOR_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_AVERAGEQUEUEWAIT, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ACTIVE_OPERATIONS_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ACTIVE_OPERATIONS_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_THROTTLE_WINDOW_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_THROTTLE_WAITING_THREADS, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_THROTTLE_ACTIVE_TOKENS, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_MERGEMETADATAREADLATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_MERGEDATAREADLATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_MERGEDATAWRITELATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_MERGE_PUT_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_MERGE_REMOVE_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLSTRIPES_THROTTLED_RPC_DIRECT_DISPATCHES.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLSTRIPES_THROTTLED_PERSISTENCE_THREAD_POLLS.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLSTRIPES_TIMEOUTS_WAITING_FOR_THROTTLE_TOKEN.rate());

        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_TEST_AND_SET_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_REQUEST_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_TEST_AND_SET_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_REQUEST_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_GET_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_GET_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_GET_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_GET_REQUEST_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_TEST_AND_SET_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_REQUEST_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_CREATEITERATOR_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_CREATEITERATOR_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_VISIT_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_VISIT_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_LOCATION_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_LOCATION_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_SPLITBUCKETS_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_JOINBUCKETS_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_DELETEBUCKETS_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_DELETEBUCKETS_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_DELETEBUCKETS_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_SETBUCKETSTATES_COUNT.rate());

        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_AVERAGEQUEUEWAITINGTIME, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_ACTIVE_WINDOW_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_BOUNCED_DUE_TO_BACK_PRESSURE.rate());
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_OK.rate());
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_MERGECHAINS_OK.rate());
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_BUSY.rate());
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_TOTAL.rate());

        return metrics;
    }

    private static Set<Metric> getDistributorMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKETS_RECHECKING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_IDEALSTATE_DIFF.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKETS_TOOFEWCOPIES.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKETS_TOOMANYCOPIES.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKETS.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKETS_NOTRUSTED.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKET_REPLICAS_MOVING_OUT.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKET_REPLICAS_COPYING_OUT.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKET_REPLICAS_COPYING_IN.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKET_REPLICAS_SYNCING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MAX_OBSERVED_TIME_SINCE_LAST_GC_SEC.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_DELETE_BUCKET_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_DELETE_BUCKET_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_DELETE_BUCKET_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_BLOCKED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_THROTTLED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_SOURCE_ONLY_COPY_CHANGED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_SOURCE_ONLY_COPY_DELETE_BLOCKED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_SOURCE_ONLY_COPY_DELETE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_SPLIT_BUCKET_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_SPLIT_BUCKET_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_SPLIT_BUCKET_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_JOIN_BUCKET_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_JOIN_BUCKET_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_JOIN_BUCKET_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_GARBAGE_COLLECTION_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_GARBAGE_COLLECTION_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_GARBAGE_COLLECTION_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_GARBAGE_COLLECTION_DOCUMENTS_REMOVED, EnumSet.of(count, rate));

        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_NOTFOUND.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_TEST_AND_SET_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_CONCURRENT_MUTATIONS.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_NOTCONNECTED.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_NOTREADY.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_WRONGDISTRIBUTOR.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_SAFE_TIME_NOT_REACHED.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_STORAGEFAILURE.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_TIMEOUT.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_BUSY.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_INCONSISTENT_BUCKET.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVES_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVES_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVES_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVES_FAILURES_NOTFOUND.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVES_FAILURES_TEST_AND_SET_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVES_FAILURES_CONCURRENT_MUTATIONS.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_FAILURES_NOTFOUND.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_FAILURES_TEST_AND_SET_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_FAILURES_CONCURRENT_MUTATIONS.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_DIVERGING_TIMESTAMP_UPDATES.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVELOCATIONS_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVELOCATIONS_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_GETS_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_GETS_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_GETS_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_GETS_FAILURES_NOTFOUND.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_NOTREADY.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_NOTCONNECTED.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_WRONGDISTRIBUTOR.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_SAFE_TIME_NOT_REACHED.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_STORAGEFAILURE.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_TIMEOUT.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_BUSY.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_INCONSISTENT_BUCKET.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_NOTFOUND.rate());

        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_DOCSSTORED.average());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_BYTESSTORED.average());

        addMetric(metrics, DistributorMetrics.VDS_BOUNCER_CLOCK_SKEW_ABORTS.count());

        return metrics;
    }

    private static void addMetric(Set<Metric> metrics, String nameWithSuffix) {
        metrics.add(new Metric(nameWithSuffix));
    }

    private static void addMetric(Set<Metric> metrics, VespaMetrics metric, EnumSet<Suffix> suffixes) {
        suffixes.forEach(suffix -> metrics.add(new Metric(metric.baseName() + "." + suffix.suffix())));
    }

    private static void addMetric(Set<Metric> metrics, String metricName, Iterable<String> aggregateSuffices) {
        for (String suffix : aggregateSuffices) {
            metrics.add(new Metric(metricName + "." + suffix));
        }
    }

}
