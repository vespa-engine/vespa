// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// TODO: This class to be used for managed Vespa.
// TODO: Vespa 9: Let this class replace DefaultMetrics.
package ai.vespa.metrics.set;

import ai.vespa.metrics.ClusterControllerMetrics;
import ai.vespa.metrics.ContainerMetrics;
import ai.vespa.metrics.DistributorMetrics;
import ai.vespa.metrics.NodeAdminMetrics;
import ai.vespa.metrics.SearchNodeMetrics;
import ai.vespa.metrics.SentinelMetrics;
import ai.vespa.metrics.StorageMetrics;

import java.util.EnumSet;
import java.util.List;

import static ai.vespa.metrics.Suffix.average;
import static ai.vespa.metrics.Suffix.count;
import static ai.vespa.metrics.Suffix.max;
import static ai.vespa.metrics.Suffix.min;
import static ai.vespa.metrics.Suffix.ninety_five_percentile;
import static ai.vespa.metrics.Suffix.ninety_nine_percentile;
import static ai.vespa.metrics.Suffix.sum;
import static ai.vespa.metrics.set.DefaultVespaMetrics.defaultVespaMetricSet;

/**
 * Metrics for the 'default' consumer, which is used by default for the generic metrics api and
 * other user facing apis, e.g. 'prometheus/'.
 *
 * @author gjoranv
 * @author yngve
 */
public class Vespa9DefaultMetricSet {

    public static final String defaultMetricSetId = "vespa9default";

    public static final MetricSet vespa9defaultMetricSet = createMetricSet();

    private static MetricSet createMetricSet() {
        return new MetricSet(defaultMetricSetId,
                List.of(),
                List.of(defaultVespaMetricSet,
                        BasicMetricSets.containerHttpStatusMetrics(),
                        getContainerMetrics(),
                        getSearchChainMetrics(),
                        getDocprocMetrics(),
                        getSearchNodeMetrics(),
                        getContentMetrics(),
                        getStorageMetrics(),
                        getDistributorMetrics(),
                        getClusterControllerMetrics(),
                        getSentinelMetrics(),
                        getOtherMetrics()));
    }

    private static MetricSet getContainerMetrics() {
        return new MetricSet.Builder("default-container")
                .metric(ContainerMetrics.JDISC_GC_MS, EnumSet.of(max, average))
                .metric(ContainerMetrics.MEM_HEAP_FREE.average())
                .metric(ContainerMetrics.FEED_LATENCY, EnumSet.of(sum, count))
                .metric(ContainerMetrics.CPU.baseName())
                .metric(ContainerMetrics.JDISC_THREAD_POOL_SIZE.max())
                .metric(ContainerMetrics.JDISC_THREAD_POOL_ACTIVE_THREADS, EnumSet.of(sum, count, min, max))
                .metric(ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_CAPACITY.max())
                .metric(ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_SIZE, EnumSet.of(sum, count, min, max))
                .metric(ContainerMetrics.SERVER_ACTIVE_THREADS.average())

                // Metrics needed for alerting
                .metric(ContainerMetrics.JDISC_SINGLETON_IS_ACTIVE.max())
                .metric(ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT.rate())
                .metric(ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS.rate())
                .metric(ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CHIFERS.rate())
                .metric(ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_UNKNOWN.rate())
                .metric(ContainerMetrics.JDISC_APPLICATION_FAILED_COMPONENT_GRAPHS.rate())
                .metric(ContainerMetrics.ATHENZ_TENANT_CERT_EXPIRY_SECONDS.min())
                .build();
    }

    private static MetricSet getSearchChainMetrics() {
        return new MetricSet.Builder("default-search-chain")
                .metric(ContainerMetrics.QUERIES.rate())
                .metric(ContainerMetrics.QUERY_LATENCY, EnumSet.of(sum, count, max, ninety_five_percentile, ninety_nine_percentile))
                .metric(ContainerMetrics.HITS_PER_QUERY, EnumSet.of(sum, count, max))
                .metric(ContainerMetrics.TOTAL_HITS_PER_QUERY, EnumSet.of(sum, count, max))
                .metric(ContainerMetrics.DEGRADED_QUERIES.rate())
                .metric(ContainerMetrics.FAILED_QUERIES.rate())
                .build();
    }

    private static MetricSet getDocprocMetrics() {
        return new MetricSet.Builder("default-docproc")
                .metric(ContainerMetrics.DOCPROC_DOCUMENTS.sum())
                .build();
    }

    private static MetricSet getSearchNodeMetrics() {
        // Metrics needed for alerting
        return new MetricSet.Builder("default-search-node")
                .metric(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK.average())
                .metric(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY.average())
                .metric(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_FEEDING_BLOCKED.max())
                .build();
    }

    private static MetricSet getContentMetrics() {
        return new MetricSet.Builder("default-content")
                .metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REQUESTED_DOCUMENTS.rate())
                .metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_LATENCY, EnumSet.of(sum, count, max))
                .metric(SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_LATENCY, EnumSet.of(sum, count, max))

                .metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_TOTAL.max())
                .metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_READY.max())
                .metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_ACTIVE.max())

                .metric(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK.average())
                .metric(SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY.average())

                .metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_MATCHED.rate())
                .metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_RERANKED.rate())
                .metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_SETUP_TIME, EnumSet.of(sum, count, max))
                .metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_LATENCY, EnumSet.of(sum, count, max))
                .metric(SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_RERANK_TIME, EnumSet.of(sum, count, max))
                .build();
    }

    private static MetricSet getStorageMetrics() {
        return new MetricSet.Builder("default-storage")
                .metric(StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_COUNT.rate())
                .metric(StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_COUNT.rate())
                .metric(StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_COUNT.rate())
                .build();
    }

    private static MetricSet getDistributorMetrics() {
        return new MetricSet.Builder("default-distributor")
                .metric(DistributorMetrics.VDS_DISTRIBUTOR_DOCSSTORED.average())

                // Metrics needed for alerting
                .metric(DistributorMetrics.VDS_BOUNCER_CLOCK_SKEW_ABORTS.count())
                .build();
    }

    private static MetricSet getClusterControllerMetrics() {
        // Metrics needed for alerting
        return new MetricSet.Builder("default-cluster-controller")
                .metric(ClusterControllerMetrics.DOWN_COUNT.max())
                .metric(ClusterControllerMetrics.MAINTENANCE_COUNT.max())
                .metric(ClusterControllerMetrics.UP_COUNT.max())
                .metric(ClusterControllerMetrics.IS_MASTER.max())
                .metric(ClusterControllerMetrics.RESOURCE_USAGE_NODES_ABOVE_LIMIT.max())
                .metric(ClusterControllerMetrics.RESOURCE_USAGE_MAX_MEMORY_UTILIZATION.max())
                .metric(ClusterControllerMetrics.RESOURCE_USAGE_MAX_DISK_UTILIZATION.max())
                .build();
    }

    private static MetricSet getSentinelMetrics() {
        // Metrics needed for alerting
        return new MetricSet.Builder("default-sentinel")
                .metric(SentinelMetrics.SENTINEL_TOTAL_RESTARTS.max())
                .build();
    }

    private static MetricSet getOtherMetrics() {
        // Metrics needed for alerting
        return new MetricSet.Builder("default-other")
                .metric(NodeAdminMetrics.ENDPOINT_CERTIFICATE_EXPIRY_SECONDS.baseName())
                .metric(NodeAdminMetrics.NODE_CERTIFICATE_EXPIRY_SECONDS.baseName())
                .build();
    }

    private Vespa9DefaultMetricSet() { }

}
