package com.yahoo.metrics;

import java.util.EnumSet;
import java.util.List;

/**
 * @author yngveaasheim
 */
public enum StorageMetrics implements VespaMetrics {

    VDS_DATASTORED_ALLDISKS_BUCKETS("vds.datastored.alldisks.buckets", Unit.BUCKET, "Number of buckets managed"),
    VDS_DATASTORED_ALLDISKS_DOCS("vds.datastored.alldisks.docs", Unit.DOCUMENT, "Number of documents stored"),
    VDS_DATASTORED_ALLDISKS_BYTES("vds.datastored.alldisks.bytes", Unit.BYTE, "Number of bytes stored"),
    VDS_VISITOR_ALLTHREADS_AVERAGEVISITORLIFETIME("vds.visitor.allthreads.averagevisitorlifetime", Unit.MILLISECOND, "Average lifetime of a visitor"),
    VDS_VISITOR_ALLTHREADS_AVERAGEQUEUEWAIT("vds.visitor.allthreads.averagequeuewait", Unit.MILLISECOND, "Average time an operation spends in input queue."),
    VDS_VISITOR_ALLTHREADS_QUEUESIZE("vds.visitor.allthreads.queuesize", Unit.OPERATION, "Size of input message queue."),
    VDS_VISITOR_ALLTHREADS_COMPLETED("vds.visitor.allthreads.completed", Unit.OPERATION, "Number of visitors completed"),
    VDS_VISITOR_ALLTHREADS_CREATED("vds.visitor.allthreads.created", Unit.OPERATION, "Number of visitors created."),
    VDS_VISITOR_ALLTHREADS_FAILED("vds.visitor.allthreads.failed", Unit.OPERATION, "Number of visitors failed"),
    VDS_VISITOR_ALLTHREADS_AVERAGEMESSAGESENDTIME("vds.visitor.allthreads.averagemessagesendtime", Unit.MILLISECOND, "Average time it takes for messages to be sent to their target (and be replied to)"),
    VDS_VISITOR_ALLTHREADS_AVERAGEPROCESSINGTIME("vds.visitor.allthreads.averageprocessingtime", Unit.MILLISECOND, "Average time used to process visitor requests"),

    VDS_FILESTOR_QUEUESIZE("vds.filestor.queuesize", Unit.OPERATION, "Size of input message queue."),
    VDS_FILESTOR_AVERAGEQUEUEWAIT("vds.filestor.averagequeuewait", Unit.MILLISECOND, "Average time an operation spends in input queue."),
    VDS_FILESTOR_ACTIVE_OPERATIONS_SIZE("vds.filestor.active_operations.size", Unit.OPERATION, "Number of concurrent active operations"),
    VDS_FILESTOR_ACTIVE_OPERATIONS_LATENCY("vds.filestor.active_operations.latency", Unit.MILLISECOND, "Latency (in ms) for completed operations"), // TODO Vespa 9: Remove 'active' from the metric name
    VDS_FILESTOR_THROTTLE_WINDOW_SIZE("vds.filestor.throttle_window_size", Unit.OPERATION, "Current size of async operation throttler window size"),
    VDS_FILESTOR_THROTTLE_WAITING_THREADS("vds.filestor.throttle_waiting_threads", Unit.THREAD, "Number of threads waiting to acquire a throttle token"),
    VDS_FILESTOR_THROTTLE_ACTIVE_TOKENS("vds.filestor.throttle_active_tokens", Unit.INSTANCE, "Current number of active throttle tokens"),
    VDS_FILESTOR_ALLTHREADS_MERGEMETADATAREADLATENCY("vds.filestor.allthreads.mergemetadatareadlatency", Unit.MILLISECOND, "Time spent in a merge step to check metadata of current node to see what data it has."),
    VDS_FILESTOR_ALLTHREADS_MERGEDATAREADLATENCY("vds.filestor.allthreads.mergedatareadlatency", Unit.MILLISECOND, "Time spent in a merge step to read data other nodes need."),
    VDS_FILESTOR_ALLTHREADS_MERGEDATAWRITELATENCY("vds.filestor.allthreads.mergedatawritelatency", Unit.MILLISECOND, "Time spent in a merge step to write data needed to current node."),
    VDS_FILESTOR_ALLTHREADS_MERGE_PUT_LATENCY("vds.filestor.allthreads.put_latency", Unit.MILLISECOND, "Latency of individual puts that are part of merge operations"), // TODO Vespa 9: Update metric name to include 'merge'
    VDS_FILESTOR_ALLTHREADS_MERGE_REMOVE_LATENCY("vds.filestor.allthreads.remove_latency", Unit.MILLISECOND, "Latency of individual removes that are part of merge operations"), // TODO Vespa 9: Update metric name to include 'merge'
    VDS_FILESTOR_ALLSTRIPES_THROTTLED_RPC_DIRECT_DISPATCHES("vds.filestor.allstripes.throttled_rpc_direct_dispatches", Unit.INSTANCE, "Number of times an RPC thread could not directly dispatch an async operation directly to Proton because it was disallowed by the throttle policy"),
    VDS_FILESTOR_ALLSTRIPES_THROTTLED_PERSISTENCE_THREAD_POLLS("vds.filestor.allstripes.throttled_persistence_thread_polls", Unit.INSTANCE, "Number of times a persistence thread could not immediately dispatch a queued async operation because it was disallowed by the throttle policy"),
    VDS_FILESTOR_ALLSTRIPES_TIMEOUTS_WAITING_FOR_THROTTLE_TOKEN("vds.filestor.allstripes.timeouts_waiting_for_throttle_token", Unit.INSTANCE, "Number of times a persistence thread timed out waiting for an available throttle policy token"),

    VDS_FILESTOR_ALLTHREADS_PUT_COUNT("vds.filestor.allthreads.put.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_PUT_FAILED("vds.filestor.allthreads.put.failed", Unit.OPERATION, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_PUT_TEST_AND_SET_FAILED("vds.filestor.allthreads.put.test_and_set_failed", Unit.OPERATION, "Number of operations that were skipped due to a test-and-set condition not met"),
    VDS_FILESTOR_ALLTHREADS_PUT_LATENCY("vds.filestor.allthreads.put.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_PUT_REQUEST_SIZE("vds.filestor.allthreads.put.request_size", Unit.BYTE, "Size of requests, in bytes"),
    VDS_FILESTOR_ALLTHREADS_REMOVE_COUNT("vds.filestor.allthreads.remove.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_REMOVE_FAILED("vds.filestor.allthreads.remove.failed", Unit.OPERATION, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_REMOVE_TEST_AND_SET_FAILED("vds.filestor.allthreads.remove.test_and_set_failed", Unit.OPERATION, "Number of operations that were skipped due to a test-and-set condition not met"),
    VDS_FILESTOR_ALLTHREADS_REMOVE_LATENCY("vds.filestor.allthreads.remove.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_REMOVE_REQUEST_SIZE("vds.filestor.allthreads.remove.request_size", Unit.BYTE, "Size of requests, in bytes"),
    VDS_FILESTOR_ALLTHREADS_GET_COUNT("vds.filestor.allthreads.get.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_GET_FAILED("vds.filestor.allthreads.get.failed", Unit.OPERATION, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_GET_LATENCY("vds.filestor.allthreads.get.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_GET_REQUEST_SIZE("vds.filestor.allthreads.get.request_size", Unit.BYTE, "Size of requests, in bytes"),
    VDS_FILESTOR_ALLTHREADS_UPDATE_COUNT("vds.filestor.allthreads.update.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_UPDATE_FAILED("vds.filestor.allthreads.update.failed", Unit.OPERATION, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_UPDATE_TEST_AND_SET_FAILED("vds.filestor.allthreads.update.test_and_set_failed", Unit.OPERATION, "Number of operations that were skipped due to a test-and-set condition not met"),
    VDS_FILESTOR_ALLTHREADS_UPDATE_LATENCY("vds.filestor.allthreads.update.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_UPDATE_REQUEST_SIZE("vds.filestor.allthreads.update.request_size", Unit.BYTE, "Size of requests, in bytes"),
    VDS_FILESTOR_ALLTHREADS_CREATEITERATOR_COUNT("vds.filestor.allthreads.createiterator.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_CREATEITERATOR_LATENCY("vds.filestor.allthreads.createiterator.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_VISIT_COUNT("vds.filestor.allthreads.visit.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_VISIT_LATENCY("vds.filestor.allthreads.visit.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_REMOVE_LOCATION_COUNT("vds.filestor.allthreads.remove_location.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_REMOVE_LOCATION_LATENCY("vds.filestor.allthreads.remove_location.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_SPLITBUCKETS_COUNT("vds.filestor.allthreads.splitbuckets.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_JOINBUCKETS_COUNT("vds.filestor.allthreads.joinbuckets.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_DELETEBUCKETS_COUNT("vds.filestor.allthreads.deletebuckets.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_DELETEBUCKETS_FAILED("vds.filestor.allthreads.deletebuckets.failed", Unit.OPERATION, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_DELETEBUCKETS_LATENCY("vds.filestor.allthreads.deletebuckets.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_SETBUCKETSTATES_COUNT("vds.filestor.allthreads.setbucketstates.count", Unit.OPERATION, "Number of requests processed."),

    VDS_MERGETHROTTLER_AVERAGEQUEUEWAITINGTIME("vds.mergethrottler.averagequeuewaitingtime", Unit.MILLISECOND, "Time merges spent in the throttler queue"),
    VDS_MERGETHROTTLER_QUEUESIZE("vds.mergethrottler.queuesize", Unit.INSTANCE, "Length of merge queue"),
    VDS_MERGETHROTTLER_ACTIVE_WINDOW_SIZE("vds.mergethrottler.active_window_size", Unit.INSTANCE, "Number of merges active within the pending window size"),
    VDS_MERGETHROTTLER_BOUNCED_DUE_TO_BACK_PRESSURE("vds.mergethrottler.bounced_due_to_back_pressure", Unit.INSTANCE, "Number of merges bounced due to resource exhaustion back-pressure"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_OK("vds.mergethrottler.locallyexecutedmerges.ok", Unit.INSTANCE, "The number of successful merges for 'locallyexecutedmerges'"),
    VDS_MERGETHROTTLER_MERGECHAINS_OK("vds.mergethrottler.mergechains.ok", Unit.INSTANCE, "The number of successful merges for 'mergechains'"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_BUSY("vds.mergethrottler.mergechains.failures.busy", Unit.INSTANCE, "The number of merges that failed because the storage node was busy"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_TOTAL("vds.mergethrottler.mergechains.failures.total", Unit.INSTANCE, "Sum of all failures");

    private final String name;
    private final Unit unit;
    private final String description;

    StorageMetrics(String name, Unit unit, String description) {
        this.name = name;
        this.unit = unit;
        this.description = description;
    }

    public String baseName() {
        return name;
    }

    public Unit unit() {
        return unit;
    }

    public String description() {
        return description;
    }

}
