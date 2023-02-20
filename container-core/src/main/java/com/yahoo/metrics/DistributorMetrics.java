package com.yahoo.metrics;

import java.util.List;

/**
 * @author yngveaasheim
 */
public enum DistributorMetrics implements VespaMetrics {

    VDS_IDEALSTATE_BUCKETS_RECHECKING("vds.idealstate.buckets_rechecking", Unit.BUCKET, "The number of buckets that we are rechecking for ideal state operations"),
    VDS_IDEALSTATE_IDEALSTATE_DIFF("vds.idealstate.idealstate_diff", Unit.BUCKET, "A number representing the current difference from the ideal state. This is a number that decreases steadily as the system is getting closer to the ideal state"),
    VDS_IDEALSTATE_BUCKETS_TOOFEWCOPIES("vds.idealstate.buckets_toofewcopies", Unit.BUCKET, "The number of buckets the distributor controls that have less than the desired redundancy"),
    VDS_IDEALSTATE_BUCKETS_TOOMANYCOPIES("vds.idealstate.buckets_toomanycopies", Unit.BUCKET, "The number of buckets the distributor controls that have more than the desired redundancy"),
    VDS_IDEALSTATE_BUCKETS("vds.idealstate.buckets", Unit.BUCKET, "The number of buckets the distributor controls"),
    VDS_IDEALSTATE_BUCKETS_NOTRUSTED("vds.idealstate.buckets_notrusted", Unit.BUCKET, "The number of buckets that have no trusted copies."),
    VDS_IDEALSTATE_BUCKET_REPLICAS_MOVING_OUT("vds.idealstate.bucket_replicas_moving_out", Unit.BUCKET, "Bucket replicas that should be moved out, e.g. retirement case or node added to cluster that has higher ideal state priority."),
    VDS_IDEALSTATE_BUCKET_REPLICAS_COPYING_OUT("vds.idealstate.bucket_replicas_copying_out", Unit.BUCKET, "Bucket replicas that should be copied out, e.g. node is in ideal state but might have to provide data other nodes in a merge"),
    VDS_IDEALSTATE_BUCKET_REPLICAS_COPYING_IN("vds.idealstate.bucket_replicas_copying_in", Unit.BUCKET, "Bucket replicas that should be copied in, e.g. node does not have a replica for a bucket that it is in ideal state for"),
    VDS_IDEALSTATE_BUCKET_REPLICAS_SYNCING("vds.idealstate.bucket_replicas_syncing", Unit.BUCKET, "Bucket replicas that need syncing due to mismatching metadata"),
    VDS_IDEALSTATE_MAX_OBSERVED_TIME_SINCE_LAST_GC_SEC("vds.idealstate.max_observed_time_since_last_gc_sec", Unit.SECOND, "Maximum time (in seconds) since GC was last successfully run for a bucket. Aggregated max value across all buckets on the distributor."),
    VDS_IDEALSTATE_DELETE_BUCKET_DONE_OK("vds.idealstate.delete_bucket.done_ok", Unit.OPERATION, "The number of operations successfully performed"),
    VDS_IDEALSTATE_DELETE_BUCKET_DONE_FAILED("vds.idealstate.delete_bucket.done_failed", Unit.OPERATION, "The number of operations that failed"),
    VDS_IDEALSTATE_DELETE_BUCKET_PENDING("vds.idealstate.delete_bucket.pending", Unit.OPERATION, "The number of operations pending"),
    VDS_IDEALSTATE_MERGE_BUCKET_DONE_OK("vds.idealstate.merge_bucket.done_ok", Unit.OPERATION, "The number of operations successfully performed"),
    VDS_IDEALSTATE_MERGE_BUCKET_DONE_FAILED("vds.idealstate.merge_bucket.done_failed", Unit.OPERATION, "The number of operations that failed"),
    VDS_IDEALSTATE_MERGE_BUCKET_PENDING("vds.idealstate.merge_bucket.pending", Unit.OPERATION, "The number of operations pending"),
    VDS_IDEALSTATE_MERGE_BUCKET_BLOCKED("vds.idealstate.merge_bucket.blocked", Unit.OPERATION, "The number of operations blocked by blocking operation starter"),
    VDS_IDEALSTATE_MERGE_BUCKET_THROTTLED("vds.idealstate.merge_bucket.throttled", Unit.OPERATION, "The number of operations throttled by throttling operation starter"),
    VDS_IDEALSTATE_MERGE_BUCKET_SOURCE_ONLY_COPY_CHANGED("vds.idealstate.merge_bucket.source_only_copy_changed", Unit.OPERATION, "The number of merge operations where source-only copy changed"),
    VDS_IDEALSTATE_MERGE_BUCKET_SOURCE_ONLY_COPY_DELETE_BLOCKED("vds.idealstate.merge_bucket.source_only_copy_delete_blocked", Unit.OPERATION, "The number of merge operations where delete of unchanged source-only copies was blocked"),
    VDS_IDEALSTATE_MERGE_BUCKET_SOURCE_ONLY_COPY_DELETE_FAILED("vds.idealstate.merge_bucket.source_only_copy_delete_failed", Unit.OPERATION, "The number of merge operations where delete of unchanged source-only copies failed"),
    VDS_IDEALSTATE_SPLIT_BUCKET_DONE_OK("vds.idealstate.split_bucket.done_ok", Unit.OPERATION, "The number of operations successfully performed"),
    VDS_IDEALSTATE_SPLIT_BUCKET_DONE_FAILED("vds.idealstate.split_bucket.done_failed", Unit.OPERATION, "The number of operations that failed"),
    VDS_IDEALSTATE_SPLIT_BUCKET_PENDING("vds.idealstate.split_bucket.pending", Unit.OPERATION, "The number of operations pending"),
    VDS_IDEALSTATE_JOIN_BUCKET_DONE_OK("vds.idealstate.join_bucket.done_ok", Unit.OPERATION, "The number of operations successfully performed"),
    VDS_IDEALSTATE_JOIN_BUCKET_DONE_FAILED("vds.idealstate.join_bucket.done_failed", Unit.OPERATION, "The number of operations that failed"),
    VDS_IDEALSTATE_JOIN_BUCKET_PENDING("vds.idealstate.join_bucket.pending", Unit.OPERATION, "The number of operations pending"),
    VDS_IDEALSTATE_GARBAGE_COLLECTION_DONE_OK("vds.idealstate.garbage_collection.done_ok", Unit.OPERATION, "The number of operations successfully performed"),
    VDS_IDEALSTATE_GARBAGE_COLLECTION_DONE_FAILED("vds.idealstate.garbage_collection.done_failed", Unit.OPERATION, "The number of operations that failed"),
    VDS_IDEALSTATE_GARBAGE_COLLECTION_PENDING("vds.idealstate.garbage_collection.pending", Unit.OPERATION, "The number of operations pending"),
    VDS_IDEALSTATE_GARBAGE_COLLECTION_DOCUMENTS_REMOVED("vds.idealstate.garbage_collection.documents_removed", Unit.DOCUMENT, "Number of documents removed by GC operations"),

    VDS_DISTRIBUTOR_PUTS_LATENCY("vds.distributor.puts.latency", Unit.MILLISECOND, "The latency of put operations"),
    VDS_DISTRIBUTOR_PUTS_OK("vds.distributor.puts.ok", Unit.OPERATION, "The number of successful put operations performed"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_TOTAL("vds.distributor.puts.failures.total", Unit.OPERATION, "Sum of all failures"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_NOTFOUND("vds.distributor.puts.failures.notfound", Unit.OPERATION, "The number of operations that failed because the document did not exist"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_TEST_AND_SET_FAILED("vds.distributor.puts.failures.test_and_set_failed", Unit.OPERATION, "The number of mutating operations that failed because they specified a test-and-set condition that did not match the existing document"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_CONCURRENT_MUTATIONS("vds.distributor.puts.failures.concurrent_mutations", Unit.OPERATION, "The number of operations that were transiently failed due to a mutating operation already being in progress for its document ID"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_NOTCONNECTED("vds.distributor.puts.failures.notconnected", Unit.OPERATION, "The number of operations discarded because there were no available storage nodes to send to"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_NOTREADY("vds.distributor.puts.failures.notready", Unit.OPERATION, "The number of operations discarded because distributor was not ready"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_WRONGDISTRIBUTOR("vds.distributor.puts.failures.wrongdistributor", Unit.OPERATION, "The number of operations discarded because they were sent to the wrong distributor"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_SAFE_TIME_NOT_REACHED("vds.distributor.puts.failures.safe_time_not_reached", Unit.OPERATION, "The number of operations that were transiently failed due to them arriving before the safe time point for bucket ownership handovers has passed"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_STORAGEFAILURE("vds.distributor.puts.failures.storagefailure", Unit.OPERATION, "The number of operations that failed in storage"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_TIMEOUT("vds.distributor.puts.failures.timeout", Unit.OPERATION, "The number of operations that failed because the operation timed out towards storage"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_BUSY("vds.distributor.puts.failures.busy", Unit.OPERATION, "The number of messages from storage that failed because the storage node was busy"),
    VDS_DISTRIBUTOR_PUTS_FAILURES_INCONSISTENT_BUCKET("vds.distributor.puts.failures.inconsistent_bucket", Unit.OPERATION, "The number of operations failed due to buckets being in an inconsistent state or not found"),
    VDS_DISTRIBUTOR_REMOVES_LATENCY("vds.distributor.removes.latency", Unit.MILLISECOND, "The latency of remove operations"),
    VDS_DISTRIBUTOR_REMOVES_OK("vds.distributor.removes.ok", Unit.OPERATION, "The number of successful removes operations performed"),
    VDS_DISTRIBUTOR_REMOVES_FAILURES_TOTAL("vds.distributor.removes.failures.total", Unit.OPERATION, "Sum of all failures"),
    VDS_DISTRIBUTOR_REMOVES_FAILURES_NOTFOUND("vds.distributor.removes.failures.notfound", Unit.OPERATION, "The number of operations that failed because the document did not exist"),
    VDS_DISTRIBUTOR_REMOVES_FAILURES_TEST_AND_SET_FAILED("vds.distributor.removes.failures.test_and_set_failed", Unit.OPERATION, "The number of mutating operations that failed because they specified a test-and-set condition that did not match the existing document"),
    VDS_DISTRIBUTOR_REMOVES_FAILURES_CONCURRENT_MUTATIONS("vds.distributor.removes.failures.concurrent_mutations", Unit.OPERATION, "The number of operations that were transiently failed due to a mutating operation already being in progress for its document ID"),
    VDS_DISTRIBUTOR_UPDATES_LATENCY("vds.distributor.updates.latency", Unit.MILLISECOND, "The latency of update operations"),
    VDS_DISTRIBUTOR_UPDATES_OK("vds.distributor.updates.ok", Unit.OPERATION, "The number of successful updates operations performed"),
    VDS_DISTRIBUTOR_UPDATES_FAILURES_TOTAL("vds.distributor.updates.failures.total", Unit.OPERATION, "Sum of all failures"),
    VDS_DISTRIBUTOR_UPDATES_FAILURES_NOTFOUND("vds.distributor.updates.failures.notfound", Unit.OPERATION, "The number of operations that failed because the document did not exist"),
    VDS_DISTRIBUTOR_UPDATES_FAILURES_TEST_AND_SET_FAILED("vds.distributor.updates.failures.test_and_set_failed", Unit.OPERATION, "The number of mutating operations that failed because they specified a test-and-set condition that did not match the existing document"),
    VDS_DISTRIBUTOR_UPDATES_FAILURES_CONCURRENT_MUTATIONS("vds.distributor.updates.failures.concurrent_mutations", Unit.OPERATION, "The number of operations that were transiently failed due to a mutating operation already being in progress for its document ID"),
    VDS_DISTRIBUTOR_UPDATES_DIVERGING_TIMESTAMP_UPDATES("vds.distributor.updates.diverging_timestamp_updates", Unit.OPERATION, "Number of updates that report they were performed against divergent version timestamps on different replicas"),
    VDS_DISTRIBUTOR_REMOVELOCATIONS_OK("vds.distributor.removelocations.ok", Unit.OPERATION, "The number of successful removelocations operations performed"),
    VDS_DISTRIBUTOR_REMOVELOCATIONS_FAILURES_TOTAL("vds.distributor.removelocations.failures.total", Unit.OPERATION, "Sum of all failures"),
    VDS_DISTRIBUTOR_GETS_LATENCY("vds.distributor.gets.latency", Unit.MILLISECOND, "The average latency of gets operations"),
    VDS_DISTRIBUTOR_GETS_OK("vds.distributor.gets.ok", Unit.OPERATION, "The number of successful gets operations performed"),
    VDS_DISTRIBUTOR_GETS_FAILURES_TOTAL("vds.distributor.gets.failures.total", Unit.OPERATION, "Sum of all failures"),
    VDS_DISTRIBUTOR_GETS_FAILURES_NOTFOUND("vds.distributor.gets.failures.notfound", Unit.OPERATION, "The number of operations that failed because the document did not exist"),
    VDS_DISTRIBUTOR_VISITOR_LATENCY("vds.distributor.visitor.latency", Unit.MILLISECOND, "The average latency of visitor operations"),
    VDS_DISTRIBUTOR_VISITOR_OK("vds.distributor.visitor.ok", Unit.OPERATION, "The number of successful visitor operations performed"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_TOTAL("vds.distributor.visitor.failures.total", Unit.OPERATION, "Sum of all failures"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_NOTREADY("vds.distributor.visitor.failures.notready", Unit.OPERATION, "The number of operations discarded because distributor was not ready"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_NOTCONNECTED("vds.distributor.visitor.failures.notconnected", Unit.OPERATION, "The number of operations discarded because there were no available storage nodes to send to"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_WRONGDISTRIBUTOR("vds.distributor.visitor.failures.wrongdistributor", Unit.OPERATION, "The number of operations discarded because they were sent to the wrong distributor"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_SAFE_TIME_NOT_REACHED("vds.distributor.visitor.failures.safe_time_not_reached", Unit.OPERATION, "The number of operations that were transiently failed due to them arriving before the safe time point for bucket ownership handovers has passed"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_STORAGEFAILURE("vds.distributor.visitor.failures.storagefailure", Unit.OPERATION, "The number of operations that failed in storage"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_TIMEOUT("vds.distributor.visitor.failures.timeout", Unit.OPERATION, "The number of operations that failed because the operation timed out towards storage"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_BUSY("vds.distributor.visitor.failures.busy", Unit.OPERATION, "The number of messages from storage that failed because the storage node was busy"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_INCONSISTENT_BUCKET("vds.distributor.visitor.failures.inconsistent_bucket", Unit.OPERATION, "The number of operations failed due to buckets being in an inconsistent state or not found"),
    VDS_DISTRIBUTOR_VISITOR_FAILURES_NOTFOUND("vds.distributor.visitor.failures.notfound", Unit.OPERATION, "The number of operations that failed because the document did not exist"),

    VDS_DISTRIBUTOR_DOCSSTORED("vds.distributor.docsstored", Unit.DOCUMENT, "Number of documents stored in all buckets controlled by this distributor"),
    VDS_DISTRIBUTOR_BYTESSTORED("vds.distributor.bytesstored", Unit.BYTE, "Number of bytes stored in all buckets controlled by this distributor"),

    VDS_BOUNCER_CLOCK_SKEW_ABORTS("vds.bouncer.clock_skew_aborts", Unit.OPERATION, "Number of client operations that were aborted due to clock skew between sender and receiver exceeding acceptable range");


    private final String name;
    private final Unit unit;
    private final String description;

    DistributorMetrics(String name, Unit unit, String description) {
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
