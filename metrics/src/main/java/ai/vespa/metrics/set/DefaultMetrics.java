// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.metrics.set;

import ai.vespa.metrics.ContainerMetrics;
import ai.vespa.metrics.SearchNodeMetrics;
import ai.vespa.metrics.StorageMetrics;
import ai.vespa.metrics.DistributorMetrics;
import ai.vespa.metrics.ClusterControllerMetrics;
import ai.vespa.metrics.SentinelMetrics;
import ai.vespa.metrics.NodeAdminMetrics;
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
import static ai.vespa.metrics.Suffix.sum;
import static ai.vespa.metrics.set.DefaultVespaMetrics.defaultVespaMetricSet;

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

        addContainerMetrics(metrics);
        addSearchChainMetrics(metrics);
        addDocprocMetrics(metrics);
        addSearchNodeMetrics(metrics);
        addContentMetrics(metrics);
        addStorageMetrics(metrics);
        addDistributorMetrics(metrics);
        addClusterControllerMetrics(metrics);
        addOtherMetrics(metrics);
        return Collections.unmodifiableSet(metrics);
    }

    private static void addContainerMetrics(Set<Metric> metrics) {
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_1XX.rate());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_2XX.rate());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_3XX.rate());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_4XX.rate());
        addMetric(metrics, ContainerMetrics.HTTP_STATUS_5XX.rate());
        addMetric(metrics, ContainerMetrics.JDISC_GC_MS.average());
        addMetric(metrics, ContainerMetrics.MEM_HEAP_FREE.average());
        addMetric(metrics, ContainerMetrics.FEED_LATENCY, EnumSet.of(sum, count));
        addMetric(metrics, ContainerMetrics.JDISC_GC_MS.max());
        // addMetric(metrics, ContainerMetrics.CPU.baseName()); // TODO: Add to container metrics
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_SIZE.max());
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_ACTIVE_THREADS, EnumSet.of(sum, count, min, max));
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_CAPACITY.max());
        addMetric(metrics, ContainerMetrics.JDISC_THREAD_POOL_WORK_QUEUE_SIZE, EnumSet.of(sum, count, min, max));
        addMetric(metrics, ContainerMetrics.SERVER_ACTIVE_THREADS.average());

        // Metrics needed for alerting
        addMetric(metrics, ContainerMetrics.JDISC_SINGLETON_IS_ACTIVE.last());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CHIFERS.rate());
        addMetric(metrics, ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_UNKNOWN.rate());
        addMetric(metrics, ContainerMetrics.JDISC_APPLICATION_FAILED_COMPONENT_GRAPHS.rate());
        addMetric(metrics, ContainerMetrics.ATHENZ_TENANT_CERT_EXPIRY_SECONDS.last());
    }

    private static void addSearchChainMetrics(Set<Metric> metrics) {
        addMetric(metrics, ContainerMetrics.QUERIES.rate());
        addMetric(metrics, ContainerMetrics.QUERY_LATENCY, EnumSet.of(sum, count, max, ninety_five_percentile, ninety_nine_percentile, average)); // TODO: Remove average with Vespa 9
        addMetric(metrics, ContainerMetrics.HITS_PER_QUERY, EnumSet.of(sum, count, max, average)); // TODO: Remove average with Vespa 9
        addMetric(metrics, ContainerMetrics.TOTAL_HITS_PER_QUERY, EnumSet.of(sum, count, max, average)); // TODO: Remove average with Vespa 9
        addMetric(metrics, ContainerMetrics.DEGRADED_QUERIES.rate());
        addMetric(metrics, ContainerMetrics.FAILED_QUERIES.rate());
    }

    private static void addDocprocMetrics(Set<Metric> metrics) {
        addMetric(metrics, ContainerMetrics.DOCPROC_DOCUMENTS.sum());
    }

    private static void addSearchNodeMetrics(Set<Metric> metrics) {
        // Metrics needed for alerting
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_FEEDING_BLOCKED, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last
    }

    private static void addContentMetrics(Set<Metric> metrics) {
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REQUESTED_DOCUMENTS.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_LATENCY, EnumSet.of(sum, count, max, average)); // TODO: Remove average with Vespa 9
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_LATENCY, EnumSet.of(sum, count, max, average)); // TODO: Remove average with Vespa 9

        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_TOTAL.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_READY.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_ACTIVE.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_DISK_USAGE.last());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MEMORY_USAGE_ALLOCATED_BYTES.last());

        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_DISK.average());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_RESOURCE_USAGE_MEMORY.average());

        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_MATCHED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_RERANKED.rate());
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_SETUP_TIME, EnumSet.of(sum, count, max, average)); // TODO: Remove average with Vespa 9
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_LATENCY, EnumSet.of(sum, count, max, average)); // TODO: Remove average with Vespa 9
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_RERANK_TIME, EnumSet.of(sum, count, max, average)); // TODO: Remove average with Vespa 9
        addMetric(metrics, SearchNodeMetrics.CONTENT_PROTON_TRANSACTIONLOG_DISK_USAGE.last());
    }

    private static void addStorageMetrics(Set<Metric> metrics) {
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_PUT_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_UPDATE_COUNT.rate());
        addMetric(metrics, StorageMetrics.VDS_FILESTOR_ALLTHREADS_REMOVE_COUNT.rate());
    }

    private static void addDistributorMetrics(Set<Metric> metrics) {
        addMetric(metrics, DistributorMetrics.VDS_DISTRIBUTOR_DOCSSTORED.average());

        // Metrics needed for alerting
        addMetric(metrics, DistributorMetrics.VDS_BOUNCER_CLOCK_SKEW_ABORTS.count());
    }

    private static void addClusterControllerMetrics(Set<Metric> metrics) {
        // Metrics needed for alerting
        addMetric(metrics, ClusterControllerMetrics.DOWN_COUNT.last());
        addMetric(metrics, ClusterControllerMetrics.MAINTENANCE_COUNT.last());
        addMetric(metrics, ClusterControllerMetrics.UP_COUNT.last());
        addMetric(metrics, ClusterControllerMetrics.IS_MASTER.last());
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_NODES_ABOVE_LIMIT, EnumSet.of(max, last)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MAX_MEMORY_UTILIZATION, EnumSet.of(last, max)); // TODO: Vespa 9: Remove last
        addMetric(metrics, ClusterControllerMetrics.RESOURCE_USAGE_MAX_DISK_UTILIZATION, EnumSet.of(last, max)); // TODO: Vespa 9: Remove last
    }

    private static void addSentinelMetrics(Set<Metric> metrics) {
        // Metrics needed for alerting
        addMetric(metrics, SentinelMetrics.SENTINEL_TOTAL_RESTARTS.last());
    }

    private static void addOtherMetrics(Set<Metric> metrics) {
        // Metrics needed for alerting
        addMetric(metrics, NodeAdminMetrics.ENDPOINT_CERTIFICATE_EXPIRY_SECONDS.baseName());
        addMetric(metrics, NodeAdminMetrics.NODE_CERTIFICATE_EXPIRY_SECONDS.baseName());
    }

    private static void addMetric(Set<Metric> metrics, String nameWithSuffix) {
        metrics.add(new Metric(nameWithSuffix));
    }

    private static void addMetric(Set<Metric> metrics, VespaMetrics metric, EnumSet<Suffix> suffixes) {
        suffixes.forEach(suffix -> metrics.add(new Metric(metric.baseName() + "." + suffix.suffix())));
    }

    private DefaultMetrics() { }

}
