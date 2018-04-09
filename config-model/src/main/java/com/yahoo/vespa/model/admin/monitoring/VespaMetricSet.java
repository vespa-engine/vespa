// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.yahoo.vespa.model.admin.monitoring.DefaultVespaMetrics.defaultVespaMetricSet;
import static java.util.Collections.singleton;

/**
 * Encapsulates vespa service metrics.
 *
 * @author gjoranv
 */
@SuppressWarnings("UnusedDeclaration") // Used by model amenders
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
        metrics.add(new Metric("sentinel.uptime.last", "sentinel.uptime"));

        metrics.add(new Metric("sentinel.running.count"));
        metrics.add(new Metric("sentinel.running.last"));

        return metrics;
    }

    private static Set<Metric> getOtherMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();
        metrics.add(new Metric("slobrok.heartbeats.failed.count", "slobrok.heartbeats.failed"));
        metrics.add(new Metric("logd.processed.lines.count", "logd.processed.lines"));
        return metrics;
    }

    private static Set<Metric> getConfigServerMetrics() {
        Set<Metric> metrics =new LinkedHashSet<>();

        metrics.add(new Metric("configserver.requests.count", "configserver.requests"));
        metrics.add(new Metric("configserver.failedRequests.count", "configserver.failedRequests"));
        metrics.add(new Metric("configserver.latency.average", "configserver.latency"));
        metrics.add(new Metric("configserver.cacheConfigElems.last", "configserver.cacheConfigElems"));
        metrics.add(new Metric("configserver.cacheChecksumElems.last", "configserver.cacheChecksumElems"));
        metrics.add(new Metric("configserver.hosts.last", "configserver.hosts"));
        metrics.add(new Metric("configserver.delayedResponses.count", "configserver.delayedResponses"));
        metrics.add(new Metric("configserver.sessionChangeErrors.count", "configserver.sessionChangeErrors"));

        return metrics;
    }

    private static Set<Metric> getContainerMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("handled.requests.count", "handled.requests"));
        metrics.add(new Metric("handled.latency.average"));
        metrics.add(new Metric("handled.latency.max"));

        metrics.add(new Metric("serverRejectedRequests.rate"));
        metrics.add(new Metric("serverRejectedRequests.count"));

        metrics.add(new Metric("serverThreadPoolSize.average"));
        metrics.add(new Metric("serverThreadPoolSize.min"));
        metrics.add(new Metric("serverThreadPoolSize.max"));
        metrics.add(new Metric("serverThreadPoolSize.rate"));
        metrics.add(new Metric("serverThreadPoolSize.count"));
        metrics.add(new Metric("serverThreadPoolSize.last"));

        metrics.add(new Metric("serverActiveThreads.average"));
        metrics.add(new Metric("serverActiveThreads.min"));
        metrics.add(new Metric("serverActiveThreads.max"));
        metrics.add(new Metric("serverActiveThreads.rate"));
        metrics.add(new Metric("serverActiveThreads.count"));
        metrics.add(new Metric("serverActiveThreads.last"));

        metrics.add(new Metric("httpapi_latency.average"));
        metrics.add(new Metric("httpapi_pending.average"));
        metrics.add(new Metric("httpapi_num_operations.rate"));
        metrics.add(new Metric("httpapi_num_updates.rate"));
        metrics.add(new Metric("httpapi_num_removes.rate"));
        metrics.add(new Metric("httpapi_num_puts.rate"));
        metrics.add(new Metric("httpapi_succeeded.rate"));
        metrics.add(new Metric("httpapi_failed.rate"));

        metrics.add(new Metric("mem.heap.total.average"));
        metrics.add(new Metric("mem.heap.free.average"));
        metrics.add(new Metric("mem.heap.used.average"));
        metrics.add(new Metric("jdisc.memory_mappings.max"));
        metrics.add(new Metric("jdisc.open_file_descriptors.max"));

        metrics.add(new Metric("jdisc.deactivated_containers.total.last"));
        metrics.add(new Metric("jdisc.deactivated_containers.with_retained_refs.last"));

        metrics.add(new Metric("athenz-tenant-cert.expiry.seconds.last", "athenz-tenant-cert.expiry.seconds"));

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
        metrics.add(new Metric("cluster-controller.cluster-state-change.count", "content.cluster-controller.cluster-state-change.count"));

        metrics.add(new Metric("cluster-controller.is-master.last"));
        // TODO(hakonhall): Update this name once persistent "count" metrics has been implemented.
        // DO NOT RELY ON THIS METRIC YET.
        metrics.add(new Metric("cluster-controller.node-event.count"));

        return metrics;
    }

    private static Set<Metric> getDocprocMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        // per chain
        metrics.add(new Metric("documents_processed.rate", "documents_processed"));

        return metrics;
    }

    private static Set<Metric> getQrserverMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("peak_qps.max", "peak_qps"));
        metrics.add(new Metric("search_connections.average", "search_connections"));
        metrics.add(new Metric("active_queries.average", "active_queries"));
        metrics.add(new Metric("feed.latency.average"));
        metrics.add(new Metric("queries.rate", "queries"));
        metrics.add(new Metric("query_latency.average", "mean_query_latency"));
        metrics.add(new Metric("query_latency.max", "max_query_latency"));
        metrics.add(new Metric("query_latency.95percentile", "95p_query_latency"));
        metrics.add(new Metric("query_latency.99percentile", "99p_query_latency"));
        metrics.add(new Metric("failed_queries.rate", "failed_queries"));
        metrics.add(new Metric("degraded_queries.rate", "degraded_queries"));
        metrics.add(new Metric("hits_per_query.average", "hits_per_query"));
        metrics.add(new Metric("documents_covered.count"));
        metrics.add(new Metric("documents_total.count"));

        metrics.add(new Metric("totalhits_per_query.average", "totalhits_per_query"));
        metrics.add(new Metric("empty_results.rate", "empty_results"));
        metrics.add(new Metric("requestsOverQuota.rate"));
        metrics.add(new Metric("requestsOverQuota.count"));

        // Errors from qrserver
        metrics.add(new Metric("error.timeout.rate","error.timeout"));
        metrics.add(new Metric("error.backends_oos.rate","error.backends_oos"));
        metrics.add(new Metric("error.plugin_failure.rate","error.plugin_failure"));
        metrics.add(new Metric("error.backend_communication_error.rate","error.backend_communication_error"));
        metrics.add(new Metric("error.empty_document_summaries.rate","error.empty_document_summaries"));
        metrics.add(new Metric("error.invalid_query_parameter.rate","error.invalid_query_parameter"));
        metrics.add(new Metric("error.internal_server_error.rate", "error.internal_server_error"));
        metrics.add(new Metric("error.misconfigured_server.rate","error.misconfigured_server"));
        metrics.add(new Metric("error.invalid_query_transformation.rate","error.invalid_query_transformation"));
        metrics.add(new Metric("error.result_with_errors.rate","error.result_with_errors"));
        metrics.add(new Metric("error.unspecified.rate","error.unspecified"));
        metrics.add(new Metric("error.unhandled_exception.rate","error.unhandled_exception"));
        metrics.add(new Metric("http.status.1xx.rate"));
        metrics.add(new Metric("http.status.2xx.rate"));
        metrics.add(new Metric("http.status.3xx.rate"));
        metrics.add(new Metric("http.status.4xx.rate"));
        metrics.add(new Metric("http.status.5xx.rate"));

        return metrics;
    }

    private static Set<Metric> getSearchNodeMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("proton.numstoreddocs.last", "documents_total"));
        metrics.add(new Metric("proton.numindexeddocs.last", "documents_ready"));
        metrics.add(new Metric("proton.numactivedocs.last", "documents_active"));
        metrics.add(new Metric("proton.numremoveddocs.last", "documents_removed"));

        metrics.add(new Metric("proton.docsinmemory.last", "documents_inmemory"));
        metrics.add(new Metric("proton.diskusage.last", "diskusage"));
        metrics.add(new Metric("proton.memoryusage.max", "content.proton.memoryusage.max"));
        metrics.add(new Metric("proton.transport.query.count.rate", "query_requests"));
        metrics.add(new Metric("proton.transport.docsum.docs.rate", "document_requests"));
        metrics.add(new Metric("proton.transport.docsum.latency.average", "content.proton.transport.docsum.latency.average"));
        metrics.add(new Metric("proton.transport.query.latency.average", "query_latency"));

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

	// Threading service
        metrics.add(new Metric("content.proton.documentdb.threading_service.master.maxpending.last"));
        metrics.add(new Metric("content.proton.documentdb.threading_service.index.maxpending.last"));
        metrics.add(new Metric("content.proton.documentdb.threading_service.summary.maxpending.last"));
        metrics.add(new Metric("content.proton.documentdb.threading_service.index_field_inverter.maxpending.last"));
        metrics.add(new Metric("content.proton.documentdb.threading_service.index_field_writer.maxpending.last"));
        metrics.add(new Metric("content.proton.documentdb.threading_service.attribute_field_writer.maxpending.last"));

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

        // resource usage
        metrics.add(new Metric("content.proton.resource_usage.disk.average"));
        metrics.add(new Metric("content.proton.resource_usage.disk_utilization.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory_utilization.average"));
        metrics.add(new Metric("content.proton.resource_usage.memory_mappings.max"));
        metrics.add(new Metric("content.proton.resource_usage.open_file_descriptors.max"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.enum_store.average"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.multi_value.average"));
        metrics.add(new Metric("content.proton.documentdb.attribute.resource_usage.feeding_blocked.last"));

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

        return metrics;
    }

    private static Set<Metric> getStorageMetrics() {
        Set<Metric> metrics = new LinkedHashSet<>();

        metrics.add(new Metric("vds.datastored.alldisks.docs.average","docs"));
        metrics.add(new Metric("vds.datastored.alldisks.bytes.average","bytes"));
        metrics.add(new Metric("vds.visitor.allthreads.averagevisitorlifetime.sum.average","visitorlifetime"));
        metrics.add(new Metric("vds.visitor.allthreads.averagequeuewait.sum.average","visitorqueuewait"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.count.rate","put"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.count.rate","remove"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.count.rate","get"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.count.rate","update"));
        metrics.add(new Metric("vds.filestor.alldisks.queuesize.average","diskqueuesize"));
        metrics.add(new Metric("vds.filestor.alldisks.averagequeuewait.sum.average","diskqueuewait"));

        metrics.add(new Metric("vds.visitor.allthreads.queuesize.count.average"));
        metrics.add(new Metric("vds.visitor.allthreads.completed.sum.average"));
        metrics.add(new Metric("vds.visitor.allthreads.created.sum.rate","visit"));

        metrics.add(new Metric("vds.filestor.alldisks.allthreads.put.sum.latency.average"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.remove.sum.latency.average"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.get.sum.latency.average"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.update.sum.latency.average"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.splitbuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.joinbuckets.count.rate"));
        metrics.add(new Metric("vds.filestor.alldisks.allthreads.setbucketstates.count.rate"));

        metrics.add(new Metric("vds.filestor.spi.put.success.average"));
        metrics.add(new Metric("vds.filestor.spi.remove.success.average"));
        metrics.add(new Metric("vds.filestor.spi.update.success.average"));
        metrics.add(new Metric("vds.filestor.spi.deleteBucket.success.average"));
        metrics.add(new Metric("vds.filestor.spi.get.success.average"));
        metrics.add(new Metric("vds.filestor.spi.iterate.success.average"));
        metrics.add(new Metric("vds.filestor.spi.put.success.rate"));
        metrics.add(new Metric("vds.filestor.spi.remove.success.rate"));
        metrics.add(new Metric("vds.filestor.spi.update.success.rate"));
        metrics.add(new Metric("vds.filestor.spi.deleteBucket.success.rate"));
        metrics.add(new Metric("vds.filestor.spi.get.success.rate"));
        metrics.add(new Metric("vds.filestor.spi.iterate.success.rate"));


        //Distributor
        metrics.add(new Metric("vds.idealstate.buckets_rechecking.average"));
        metrics.add(new Metric("vds.idealstate.idealstate_diff.average"));
        metrics.add(new Metric("vds.idealstate.buckets_toofewcopies.average"));
        metrics.add(new Metric("vds.idealstate.buckets_toomanycopies.average"));
        metrics.add(new Metric("vds.idealstate.buckets.average"));
        metrics.add(new Metric("vds.idealstate.buckets_notrusted.average"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.done_ok.rate","deleteok"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.done_failed.rate","deletefailed"));
        metrics.add(new Metric("vds.idealstate.delete_bucket.pending.average","deletepending"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.done_ok.rate","mergeok"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.done_failed.rate","mergefailed"));
        metrics.add(new Metric("vds.idealstate.merge_bucket.pending.average","mergepending"));
        metrics.add(new Metric("vds.idealstate.split_bucket.done_ok.rate","splitok"));
        metrics.add(new Metric("vds.idealstate.split_bucket.done_failed.rate","splitfailed"));
        metrics.add(new Metric("vds.idealstate.split_bucket.pending.average","splitpending"));
        metrics.add(new Metric("vds.idealstate.join_bucket.done_ok.rate","joinok"));
        metrics.add(new Metric("vds.idealstate.join_bucket.done_failed.rate","joinfailed"));
        metrics.add(new Metric("vds.idealstate.join_bucket.pending.average","joinpending"));

        metrics.add(new Metric("vds.distributor.puts.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.puts.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.puts.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.removes.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.removes.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.removes.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.updates.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.updates.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.removelocations.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.removelocations.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.removelocations.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.gets.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.gets.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.gets.sum.failures.total.rate"));
        metrics.add(new Metric("vds.distributor.visitor.sum.latency.average"));
        metrics.add(new Metric("vds.distributor.visitor.sum.ok.rate"));
        metrics.add(new Metric("vds.distributor.visitor.sum.failures.total.rate"));

        metrics.add(new Metric("vds.distributor.docsstored.average"));
        metrics.add(new Metric("vds.distributor.bytesstored.average"));

        return metrics;
    }

}
