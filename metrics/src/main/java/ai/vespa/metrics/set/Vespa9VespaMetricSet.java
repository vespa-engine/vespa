// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// TODO: This class to be used for managed Vespa.
// TODO: Vespa 9: Let this class replace VespaMetricSet.
package ai.vespa.metrics.set;

import ai.vespa.metrics.ClusterControllerMetrics;
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
import java.util.List;
import java.util.Set;

import static ai.vespa.metrics.Suffix.average;
import static ai.vespa.metrics.Suffix.count;
import static ai.vespa.metrics.Suffix.max;
import static ai.vespa.metrics.Suffix.min;
import static ai.vespa.metrics.Suffix.ninety_five_percentile;
import static ai.vespa.metrics.Suffix.ninety_nine_percentile;
import static ai.vespa.metrics.Suffix.rate;
import static ai.vespa.metrics.Suffix.sum;
import static ai.vespa.metrics.set.DefaultVespaMetrics.defaultVespaMetricSet;

/**
 * Encapsulates vespa service metrics.
 *
 * @author gjoranv
 * @author Yngve Aasheim
 */
public class Vespa9VespaMetricSet {

    public static final MetricSet vespa9vespaMetricSet = createMetricSet();

    private static MetricSet createMetricSet() {
        return new MetricSet("vespa9vespa",
                getVespaMetrics(),
                List.of(defaultVespaMetricSet,
                        BasicMetricSets.containerHttpStatusMetrics(),
                        MicrometerMetrics.asMetricSet()));
    }

    private static Set<Metric> getVespaMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.addAll(getSearchNodeMetrics());
        metrics.addAll(getStorageMetrics());
        metrics.addAll(getDistributorMetrics());
        metrics.addAll(getDocprocMetrics());
        metrics.addAll(getClusterControllerMetrics());
        metrics.addAll(getSearchChainMetrics());
        metrics.addAll(getContainerMetrics());
        metrics.addAll(getSentinelMetrics());
        metrics.addAll(getOtherMetrics());

        return Collections.unmodifiableSet(metrics);
    }

    private static Set<Metric> getSentinelMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, SentinelMetrics.SENTINEL_RESTARTS.count());
        addMetric(metrics, SentinelMetrics.SENTINEL_TOTAL_RESTARTS.max());

        return metrics;
    }

    private static Set<Metric> getOtherMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, SlobrokMetrics.SLOBROK_MISSING_CONSENSUS.count());

        addMetric(metrics, LogdMetrics.LOGD_PROCESSED_LINES.count());

        // Java (JRT) TLS metrics
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_TLS_CERTIFICATE_VERIFICATION_FAILURES.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_PEER_AUTHORIZATION_FAILURES.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_SERVER_TLS_CONNECTIONS_ESTABLISHED.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_CLIENT_TLS_CONNECTIONS_ESTABLISHED.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_SERVER_UNENCRYPTED_CONNECTIONS_ESTABLISHED.baseName());
        addMetric(metrics, ContainerMetrics.JRT_TRANSPORT_CLIENT_UNENCRYPTED_CONNECTIONS_ESTABLISHED.baseName());

        // NodeAdmin certificate
        addMetric(metrics, NodeAdminMetrics.ENDPOINT_CERTIFICATE_EXPIRY_SECONDS.baseName());
        addMetric(metrics, NodeAdminMetrics.NODE_CERTIFICATE_EXPIRY_SECONDS.baseName());

        // Routing layer metrics
        addMetric(metrics, RoutingLayerMetrics.WORKER_CONNECTIONS.max()); // Hosted Vespa only (routing layer)
        return metrics;
    }


    private static Set<Metric> getContainerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ContainerMetrics.APPLICATION_GENERATION.baseName());

        addMetric(metrics, ContainerMetrics.HANDLED_REQUESTS.count());
        addMetric(metrics, ContainerMetrics.HANDLED_LATENCY, EnumSet.of(sum, count, max));

        addMetric(metrics, ContainerMetrics.SERVER_NUM_OPEN_CONNECTIONS, EnumSet.of(max, average));
        addMetric(metrics, ContainerMetrics.SERVER_NUM_CONNECTIONS, EnumSet.of(max, average));

        addMetric(metrics, ContainerMetrics.SERVER_BYTES_RECEIVED, EnumSet.of(sum, count));
        addMetric(metrics, ContainerMetrics.SERVER_BYTES_SENT, EnumSet.of(sum, count));

        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_UNHANDLED_EXCEPTIONS, EnumSet.of(sum, count));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_CAPACITY, EnumSet.of(max));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_SIZE, EnumSet.of(sum, count, min, max));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_REJECTED_TASKS, EnumSet.of(sum));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_SIZE.max());
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_MAX_ALLOWED_SIZE.max());
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_ACTIVE_THREADS, EnumSet.of(sum, count, min, max));

        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_BUSY_THREADS, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_TOTAL_THREADS.max());
        addMetric(metrics, ContainerMetrics.JETTY_THREADPOOL_QUEUE_SIZE.max());

        addMetric(metrics, ContainerMetrics.HTTPAPI_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.HTTPAPI_PENDING, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.HTTPAPI_NUM_UPDATES.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_NUM_REMOVES.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_NUM_PUTS.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_SUCCEEDED.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_FAILED.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_PARSE_ERROR.rate());
        addMetric(metrics, ContainerMetrics.HTTPAPI_FAILED_INSUFFICIENT_STORAGE.rate());

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

        addMetric(metrics, ContainerMetrics.JDISC_GC_MS.max());
        addMetric(metrics, ContainerMetrics.CPU.baseName());

        addMetric(metrics, ContainerMetrics.JDISC_DEACTIVATED_CONTAINERS_TOTAL.sum());

        addMetric(metrics, ContainerMetrics.JDISC_SINGLETON_IS_ACTIVE, EnumSet.of(min, max));

        addMetric(metrics, ContainerMetrics.JDISC_HTTP_REQUEST_PREMATURELY_CLOSED.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_REQUEST_CONTENT_SIZE, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_REQUESTS.count());

        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_EXPIRED_CLIENT_CERT.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CHIFERS.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_CONNECTION_CLOSED.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_UNKNOWN.rate());

        addMetric(metrics, ContainerMetrics.JDISC_HTTP_FILTER_RULE_BLOCKED_REQUESTS.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_FILTER_RULE_ALLOWED_REQUESTS.rate());

        addMetric(metrics, ContainerMetrics.JDISC_HTTP_HANDLER_UNHANDLED_EXCEPTIONS.rate());

        addMetric(metrics, ContainerMetrics.JDISC_APPLICATION_FAILED_COMPONENT_GRAPHS.rate());

        addMetric(metrics, ContainerMetrics.JDISC_RENDER_LATENCY, EnumSet.of(max, count, sum));

        addMetric(metrics, ContainerMetrics.FEED_LATENCY, EnumSet.of(sum, count, max));

        // Embedders
        addMetric(metrics, ContainerMetrics.EMBEDDER_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.EMBEDDER_SEQUENCE_LENGTH, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.EMBEDDER_REQUEST_COUNT, EnumSet.of(count));
        addMetric(metrics, ContainerMetrics.EMBEDDER_REQUEST_FAILURE_COUNT, EnumSet.of(count));
        addMetric(metrics, ContainerMetrics.EMBEDDER_BATCH_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.EMBEDDER_BATCH_QUEUE_TIME, EnumSet.of(max, sum, count));
        addMetric(metrics, ContainerMetrics.EMBEDDER_BATCH_COUNT, EnumSet.of(count));

        return metrics;
    }

    private static Set<Metric> getClusterControllerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ClusterControllerMetrics.DOWN_COUNT.max());
        addMetric(metrics, ClusterControllerMetrics.INITIALIZING_COUNT.max());
        addMetric(metrics, ClusterControllerMetrics.MAINTENANCE_COUNT.max());
        addMetric(metrics, ClusterControllerMetrics.RETIRED_COUNT.max());
        addMetric(metrics, ClusterControllerMetrics.UP_COUNT.max());
        addMetric(metrics, ClusterControllerMetrics.NODES_NOT_CONVERGED.max());
        addMetric(metrics, ClusterControllerMetrics.CLUSTER_BUCKETS_OUT_OF_SYNC_RATIO.max());
        addMetric(metrics, ClusterControllerMetrics.STORED_DOCUMENT_COUNT.max());
        addMetric(metrics, ClusterControllerMetrics.CLUSTER_STATE_CHANGE_COUNT.baseName());

        addMetric(metrics, ClusterControllerMetrics.WORK_MS, EnumSet.of(sum, count));

        addMetric(metrics, ClusterControllerMetrics.IS_MASTER.max());

        // TODO(hakonhall): Update this name once persistent "count" metrics has been implemented.
        // DO NOT RELY ON THIS METRIC YET.
        addMetric(metrics, ClusterControllerMetrics.NODE_EVENT_COUNT.baseName());
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_NODES_ABOVE_LIMIT.max());
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MAX_MEMORY_UTILIZATION.max());
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MAX_DISK_UTILIZATION.max());
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MEMORY_LIMIT.max());
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_DISK_LIMIT.max());
        addMetric(metrics, ClusterControllerMetrics.REINDEXING_PROGRESS.max());

        return metrics;
    }

    private static Set<Metric> getDocprocMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        // per chain
        metrics.add(new Metric("documents_processed.rate"));

        addMetric(metrics, ContainerMetrics.DOCPROC_PROC_TIME, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.DOCPROC_DOCUMENTS, EnumSet.of(sum));

        return metrics;
    }

    private static Set<Metric> getSearchChainMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        addMetric(metrics, ContainerMetrics.PEAK_QPS.max());
        addMetric(metrics, ContainerMetrics.QUERIES.rate());
        addMetric(metrics, ContainerMetrics.QUERY_CONTAINER_LATENCY, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.QUERY_LATENCY, EnumSet.of(sum, count, max, ninety_five_percentile, ninety_nine_percentile));
        addMetric(metrics, ContainerMetrics.QUERY_TIMEOUT, EnumSet.of(sum, count, max, min, ninety_five_percentile, ninety_nine_percentile));
        addMetric(metrics, ContainerMetrics.FAILED_QUERIES.rate());
        addMetric(metrics, ContainerMetrics.DEGRADED_QUERIES.rate());
        addMetric(metrics, ContainerMetrics.HITS_PER_QUERY, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.SEARCH_CONNECTIONS, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.QUERY_HIT_OFFSET, EnumSet.of(sum, count, max));
        addMetric(metrics, ContainerMetrics.DOCUMENTS_COVERED.count());
        addMetric(metrics, ContainerMetrics.DOCUMENTS_TOTAL.count());
        addMetric(metrics, ContainerMetrics.DOCUMENTS_TARGET_TOTAL.count());
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

        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_TOTAL.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_READY.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_ACTIVE.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_REMOVED.max());

        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_DOCS_IN_MEMORY.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_HEART_BEAT_AGE.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCSUM_LATENCY, EnumSet.of(max, sum, count));

        // Search protocol
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_REQUEST_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_REPLY_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REQUEST_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REPLY_SIZE, EnumSet.of(max, sum, count));

        // Executors shared between all document dbs
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_PROTON_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_PROTON_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_PROTON_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FLUSH_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FLUSH_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_MATCH_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_MATCH_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_MATCH_WAKEUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_MATCH_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_DOCSUM_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_DOCSUM_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_DOCSUM_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_SHARED_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_SHARED_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_SHARED_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_WARMUP_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_WARMUP_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_WARMUP_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FIELD_WRITER_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FIELD_WRITER_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FIELD_WRITER_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_EXECUTOR_FIELD_WRITER_SATURATION, EnumSet.of(max, sum, count));

        // jobs
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
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_UTILIZATION, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_ACCEPTED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_UTILIZATION, EnumSet.of(max, sum, count));

        // lid space
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_LID_LIMIT.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_USED_LIDS.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_LID_LIMIT.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_USED_LIDS.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_LID_LIMIT.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_USED_LIDS.max());

        // bucket move
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_BUCKET_MOVE_BUCKETS_PENDING.max());

        // resource usage
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_TOTAL.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_TOTAL_UTILIZATION.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_TRANSIENT.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_RESERVED.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_USED_AND_RESERVED.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY_USAGE_TOTAL.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY_USAGE_TOTAL_UTILIZATION.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY_USAGE_TRANSIENT.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY_MAPPINGS.max());
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

        // document store
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_DISK_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_DISK_BLOAT.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MAX_BUCKET_SPREAD.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MEMORY_USAGE_ALLOCATED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MEMORY_USAGE_ALLOCATED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MEMORY_USAGE_ALLOCATED_BYTES.average());

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
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_ATTRIBUTE_DISK_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_NOTREADY_ATTRIBUTE_MEMORY_USAGE_ALLOCATED_BYTES.average());

        // index
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_INDEXES.max());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_MEMORY_USAGE_ALLOCATED_BYTES.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_IO_SEARCH_READ_BYTES, EnumSet.of(sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_INDEX_IO_SEARCH_CACHED_READ_BYTES, EnumSet.of(sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_READY_INDEX_DISK_USAGE.average());

        // index caches
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_INDEX_CACHE_POSTINGLIST_MEMORY_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_INDEX_CACHE_POSTINGLIST_HIT_RATE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_INDEX_CACHE_POSTINGLIST_LOOKUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_INDEX_CACHE_POSTINGLIST_INVALIDATIONS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_INDEX_CACHE_BITVECTOR_MEMORY_USAGE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_INDEX_CACHE_BITVECTOR_HIT_RATE.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_INDEX_CACHE_BITVECTOR_LOOKUPS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_INDEX_CACHE_BITVECTOR_INVALIDATIONS.rate());

        // matching
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERIES.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERY_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERY_SETUP_TIME, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_MATCHED, EnumSet.of(rate));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_EXACT_NNS_DISTANCES_COMPUTED, EnumSet.of(rate));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_APPROXIMATE_NNS_DISTANCES_COMPUTED, EnumSet.of(rate));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_APPROXIMATE_NNS_NODES_VISITED, EnumSet.of(rate));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERIES.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_SOFT_DOOMED_QUERIES.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_SOFT_DOOM_FACTOR, EnumSet.of(min, max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_SETUP_TIME, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_GROUPING_TIME, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_RERANK_TIME, EnumSet.of(max, sum, count));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCS_MATCHED, EnumSet.of(rate, count));
        /* Temporarily remove to reduce metrics volume
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_EXACT_NNS_DISTANCES_COMPUTED, EnumSet.of(rate));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_APPROXIMATE_NNS_DISTANCES_COMPUTED, EnumSet.of(rate));
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_APPROXIMATE_NNS_NODES_VISITED, EnumSet.of(rate));
         */
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
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_GET_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_GET_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_GET_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_LATENCY, EnumSet.of(max, sum, count));
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
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_BY_GID_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_BY_GID_FAILED.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_BY_GID_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_SETBUCKETSTATES_COUNT.rate());

        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_AVERAGEQUEUEWAITINGTIME, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_QUEUESIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_ACTIVE_WINDOW_SIZE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_ESTIMATED_MERGE_MEMORY_USAGE, EnumSet.of(max, sum, count));
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_BOUNCED_DUE_TO_BACK_PRESSURE.rate());
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_OK.rate());
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_MERGECHAINS_OK.rate());
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_BUSY.rate());
        addMetric(metrics, StorageMetrics.VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_TOTAL.rate());

        return metrics;
    }

    private static Set<Metric> getDistributorMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKETS_TOOFEWCOPIES.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_BUCKETS_TOOMANYCOPIES.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_DELETE_BUCKET_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_DELETE_BUCKET_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_DELETE_BUCKET_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_SPLIT_BUCKET_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_SPLIT_BUCKET_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_SPLIT_BUCKET_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_JOIN_BUCKET_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_JOIN_BUCKET_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_JOIN_BUCKET_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_GARBAGE_COLLECTION_DONE_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_GARBAGE_COLLECTION_DONE_FAILED.rate());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_GARBAGE_COLLECTION_PENDING.average());
        addMetric(metrics, DistributorMetrics.VDS_IDEALSTATE_GARBAGE_COLLECTION_DOCUMENTS_REMOVED, EnumSet.of(rate));

        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_PUTS_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVES_LATENCY, EnumSet.of(max));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVES_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_REMOVES_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_UPDATES_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_GETS_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_GETS_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_GETS_FAILURES_TOTAL.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_LATENCY, EnumSet.of(max, sum, count));
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_OK.rate());
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_VISITOR_FAILURES_TOTAL.rate());

        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_DOCSSTORED.average());

        addMetric(metrics, DistributorMetrics.VDS_BOUNCER_CLOCK_SKEW_ABORTS.count());

        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_MUTATING_OP_MEMORY_USAGE, EnumSet.of(max));

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
