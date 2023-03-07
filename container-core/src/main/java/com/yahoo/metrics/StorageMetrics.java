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
    VDS_DATASTORED_ALLDISKS_ACTIVEBUCKETS("vds.datastored.alldisks.activebuckets", Unit.BUCKET, "Number of active buckets on the node"),
    VDS_DATASTORED_ALLDISKS_READYBUCKETS("vds.datastored.alldisks.readybuckets", Unit.BUCKET, "Number of ready buckets on the node"),

    VDS_VISITOR_ALLTHREADS_AVERAGEVISITORLIFETIME("vds.visitor.allthreads.averagevisitorlifetime", Unit.MILLISECOND, "Average lifetime of a visitor"),
    VDS_VISITOR_ALLTHREADS_AVERAGEQUEUEWAIT("vds.visitor.allthreads.averagequeuewait", Unit.MILLISECOND, "Average time an operation spends in input queue."),
    VDS_VISITOR_ALLTHREADS_QUEUESIZE("vds.visitor.allthreads.queuesize", Unit.OPERATION, "Size of input message queue."),
    VDS_VISITOR_ALLTHREADS_COMPLETED("vds.visitor.allthreads.completed", Unit.OPERATION, "Number of visitors completed"),
    VDS_VISITOR_ALLTHREADS_CREATED("vds.visitor.allthreads.created", Unit.OPERATION, "Number of visitors created."),
    VDS_VISITOR_ALLTHREADS_FAILED("vds.visitor.allthreads.failed", Unit.OPERATION, "Number of visitors failed"),
    VDS_VISITOR_ALLTHREADS_AVERAGEMESSAGESENDTIME("vds.visitor.allthreads.averagemessagesendtime", Unit.MILLISECOND, "Average time it takes for messages to be sent to their target (and be replied to)"),
    VDS_VISITOR_ALLTHREADS_AVERAGEPROCESSINGTIME("vds.visitor.allthreads.averageprocessingtime", Unit.MILLISECOND, "Average time used to process visitor requests"),
    VDS_VISITOR_ALLTHREADS_ABORTED("vds.visitor.allthreads.aborted", Unit.INSTANCE, "Number of visitors aborted."),
    VDS_VISITOR_ALLTHREADS_AVERAGEVISITORCREATIONTIME("vds.visitor.allthreads.averagevisitorcreationtime", Unit.MILLISECOND, "Average time spent creating a visitor instance"),
    VDS_VISITOR_ALLTHREADS_DESTINATION_FAILURE_REPLIES("vds.visitor.allthreads.destination_failure_replies", Unit.INSTANCE, "Number of failure replies received from the visitor destination"),

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
    VDS_FILESTOR_ALLTHREADS_MERGEAVGDATARECEIVEDNEEDED("vds.filestor.allthreads.mergeavgdatareceivedneeded", Unit.BYTE, "Amount of data transferred from previous node in chain that we needed to apply locally."),
    VDS_FILESTOR_ALLTHREADS_MERGEBUCKETS_COUNT("vds.filestor.allthreads.mergebuckets.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_MERGEBUCKETS_FAILED("vds.filestor.allthreads.mergebuckets.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_MERGEBUCKETS_LATENCY("vds.filestor.allthreads.mergebuckets.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_MERGELATENCYTOTAL("vds.filestor.allthreads.mergelatencytotal", Unit.MILLISECOND, "Latency of total merge operation, from master node receives it, until merge is complete and master node replies."),
    VDS_FILESTOR_ALLTHREADS_MERGE_PUT_LATENCY("vds.filestor.allthreads.put_latency", Unit.MILLISECOND, "Latency of individual puts that are part of merge operations"), // TODO Vespa 9: Update metric name to include 'merge'
    VDS_FILESTOR_ALLTHREADS_MERGE_REMOVE_LATENCY("vds.filestor.allthreads.remove_latency", Unit.MILLISECOND, "Latency of individual removes that are part of merge operations"), // TODO Vespa 9: Update metric name to include 'merge'
    VDS_FILESTOR_ALLSTRIPES_THROTTLED_RPC_DIRECT_DISPATCHES("vds.filestor.allstripes.throttled_rpc_direct_dispatches", Unit.INSTANCE, "Number of times an RPC thread could not directly dispatch an async operation directly to Proton because it was disallowed by the throttle policy"),
    VDS_FILESTOR_ALLSTRIPES_THROTTLED_PERSISTENCE_THREAD_POLLS("vds.filestor.allstripes.throttled_persistence_thread_polls", Unit.INSTANCE, "Number of times a persistence thread could not immediately dispatch a queued async operation because it was disallowed by the throttle policy"),
    VDS_FILESTOR_ALLSTRIPES_TIMEOUTS_WAITING_FOR_THROTTLE_TOKEN("vds.filestor.allstripes.timeouts_waiting_for_throttle_token", Unit.INSTANCE, "Number of times a persistence thread timed out waiting for an available throttle policy token"),
    VDS_FILESTOR_ALLSTRIPES_AVERAGEQUEUEWAIT("vds.filestor.allstripes.averagequeuewait", Unit.MILLISECOND, "Average time an operation spends in input queue."),

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
    VDS_FILESTOR_ALLTHREADS_REMOVE_NOT_FOUND("vds.filestor.allthreads.remove.not_found", Unit.REQUEST, "Number of requests that could not be completed due to source document not found."),

    VDS_FILESTOR_ALLTHREADS_GET_COUNT("vds.filestor.allthreads.get.count", Unit.OPERATION, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_GET_FAILED("vds.filestor.allthreads.get.failed", Unit.OPERATION, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_GET_LATENCY("vds.filestor.allthreads.get.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_GET_REQUEST_SIZE("vds.filestor.allthreads.get.request_size", Unit.BYTE, "Size of requests, in bytes"),
    VDS_FILESTOR_ALLTHREADS_GET_NOT_FOUND("vds.filestor.allthreads.get.not_found", Unit.REQUEST, "Number of requests that could not be completed due to source document not found."),
    VDS_FILESTOR_ALLTHREADS_UPDATE_COUNT("vds.filestor.allthreads.update.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_UPDATE_FAILED("vds.filestor.allthreads.update.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_UPDATE_TEST_AND_SET_FAILED("vds.filestor.allthreads.update.test_and_set_failed", Unit.REQUEST, "Number of requests that were skipped due to a test-and-set condition not met"),
    VDS_FILESTOR_ALLTHREADS_UPDATE_LATENCY("vds.filestor.allthreads.update.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_UPDATE_REQUEST_SIZE("vds.filestor.allthreads.update.request_size", Unit.BYTE, "Size of requests, in bytes"),
    VDS_FILESTOR_ALLTHREADS_UPDATE_LATENCY_READ("vds.filestor.allthreads.update.latency_read", Unit.MILLISECOND, "Latency of the source read in the request."),
    VDS_FILESTOR_ALLTHREADS_UPDATE_NOT_FOUND("vds.filestor.allthreads.update.not_found", Unit.REQUEST, "Number of requests that could not be completed due to source document not found."),
    VDS_FILESTOR_ALLTHREADS_CREATEITERATOR_COUNT("vds.filestor.allthreads.createiterator.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_CREATEITERATOR_LATENCY("vds.filestor.allthreads.createiterator.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_CREATEITERATOR_FAILED("vds.filestor.allthreads.createiterator.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_VISIT_COUNT("vds.filestor.allthreads.visit.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_VISIT_LATENCY("vds.filestor.allthreads.visit.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_VISIT_DOCS("vds.filestor.allthreads.visit.docs", Unit.DOCUMENT, "Number of entries read per iterate call"),
    VDS_FILESTOR_ALLTHREADS_VISIT_FAILED("vds.filestor.allthreads.visit.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_REMOVE_LOCATION_COUNT("vds.filestor.allthreads.remove_location.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_REMOVE_LOCATION_LATENCY("vds.filestor.allthreads.remove_location.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_REMOVE_LOCATION_FAILED("vds.filestor.allthreads.remove_location.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_SPLITBUCKETS_COUNT("vds.filestor.allthreads.splitbuckets.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_SPLITBUCKETS_FAILED("vds.filestor.allthreads.splitbuckets.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_SPLITBUCKETS_LATENCY("vds.filestor.allthreads.splitbuckets.latency", Unit.REQUEST, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_JOINBUCKETS_COUNT("vds.filestor.allthreads.joinbuckets.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_JOINBUCKETS_FAILED("vds.filestor.allthreads.joinbuckets.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_JOINBUCKETS_LATENCY("vds.filestor.allthreads.joinbuckets.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_DELETEBUCKETS_COUNT("vds.filestor.allthreads.deletebuckets.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_DELETEBUCKETS_FAILED("vds.filestor.allthreads.deletebuckets.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_DELETEBUCKETS_LATENCY("vds.filestor.allthreads.deletebuckets.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_SETBUCKETSTATES_COUNT("vds.filestor.allthreads.setbucketstates.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_SETBUCKETSTATES_FAILED("vds.filestor.allthreads.setbucketstates.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_SETBUCKETSTATES_LATENCY("vds.filestor.allthreads.setbucketstates.latency", Unit.MILLISECOND, "Latency of successful requests."),

    VDS_MERGETHROTTLER_AVERAGEQUEUEWAITINGTIME("vds.mergethrottler.averagequeuewaitingtime", Unit.MILLISECOND, "Time merges spent in the throttler queue"),
    VDS_MERGETHROTTLER_QUEUESIZE("vds.mergethrottler.queuesize", Unit.INSTANCE, "Length of merge queue"),
    VDS_MERGETHROTTLER_ACTIVE_WINDOW_SIZE("vds.mergethrottler.active_window_size", Unit.INSTANCE, "Number of merges active within the pending window size"),
    VDS_MERGETHROTTLER_BOUNCED_DUE_TO_BACK_PRESSURE("vds.mergethrottler.bounced_due_to_back_pressure", Unit.INSTANCE, "Number of merges bounced due to resource exhaustion back-pressure"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_OK("vds.mergethrottler.locallyexecutedmerges.ok", Unit.INSTANCE, "The number of successful merges for 'locallyexecutedmerges'"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_ABORTED("vds.mergethrottler.locallyexecutedmerges.failures.aborted", Unit.OPERATION, "The number of merges that failed because the storage node was (most likely) shutting down"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_BUCKETNOTFOUND("vds.mergethrottler.locallyexecutedmerges.failures.bucketnotfound", Unit.OPERATION, "The number of operations that failed because the bucket did not exist"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_BUSY("vds.mergethrottler.locallyexecutedmerges.failures.busy", Unit.OPERATION, "The number of merges that failed because the storage node was busy"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_EXISTS("vds.mergethrottler.locallyexecutedmerges.failures.exists", Unit.OPERATION, "The number of merges that were rejected due to a merge operation for their bucket already being processed"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_NOTREADY("vds.mergethrottler.locallyexecutedmerges.failures.notready", Unit.OPERATION, "The number of merges discarded because distributor was not ready"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_OTHER("vds.mergethrottler.locallyexecutedmerges.failures.other", Unit.OPERATION, "The number of other failures"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_REJECTED("vds.mergethrottler.locallyexecutedmerges.failures.rejected", Unit.OPERATION, "The number of merges that were rejected"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_TIMEOUT("vds.mergethrottler.locallyexecutedmerges.failures.timeout", Unit.OPERATION, "The number of merges that failed because they timed out towards storage"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_TOTAL("vds.mergethrottler.locallyexecutedmerges.failures.total", Unit.OPERATION, "Sum of all failures"),
    VDS_MERGETHROTTLER_LOCALLYEXECUTEDMERGES_FAILURES_WRONGDISTRIBUTION("vds.mergethrottler.locallyexecutedmerges.failures.wrongdistribution", Unit.OPERATION, "The number of merges that were discarded (flushed) because they were initiated at an older cluster state than the current"),
    VDS_MERGETHROTTLER_MERGECHAINS_OK("vds.mergethrottler.mergechains.ok", Unit.OPERATION, "The number of successful merges for 'mergechains'"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_BUSY("vds.mergethrottler.mergechains.failures.busy", Unit.OPERATION, "The number of merges that failed because the storage node was busy"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_TOTAL("vds.mergethrottler.mergechains.failures.total", Unit.OPERATION, "Sum of all failures"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_EXISTS("vds.mergethrottler.mergechains.failures.exists", Unit.OPERATION, "The number of merges that were rejected due to a merge operation for their bucket already being processed"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_NOTREADY("vds.mergethrottler.mergechains.failures.notready", Unit.OPERATION, "The number of merges discarded because distributor was not ready"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_OTHER("vds.mergethrottler.mergechains.failures.other", Unit.OPERATION, "The number of other failures"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_REJECTED("vds.mergethrottler.mergechains.failures.rejected", Unit.OPERATION, "The number of merges that were rejected"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_TIMEOUT("vds.mergethrottler.mergechains.failures.timeout", Unit.OPERATION, "The number of merges that failed because they timed out towards storage"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_WRONGDISTRIBUTION("vds.mergethrottler.mergechains.failures.wrongdistribution", Unit.OPERATION, "The number of merges that were discarded (flushed) because they were initiated at an older cluster state than the current"),


    // C++ TLS metrics - these come from both the distributor and storage
    VDS_SERVER_NETWORK_TLS_HANDSHAKES_FAILED("vds.server.network.tls-handshakes-failed", Unit.OPERATION, "Number of client or server connection attempts that failed during TLS handshaking"),
    VDS_SERVER_NETWORK_PEER_AUTHORIZATION_FAILURES("vds.server.network.peer-authorization-failures", Unit.FAILURE, "Number of TLS connection attempts failed due to bad or missing peer certificate credentials"),
    VDS_SERVER_NETWORK_CLIENT_TLS_CONNECTIONS_ESTABLISHED("vds.server.network.client.tls-connections-established", Unit.CONNECTION, "Number of secure mTLS connections established"),
    VDS_SERVER_NETWORK_SERVER_TLS_CONNECTIONS_ESTABLISHED("vds.server.network.server.tls-connections-established", Unit.CONNECTION, "Number of secure mTLS connections established"),
    VDS_SERVER_NETWORK_CLIENT_INSECURE_CONNECTIONS_ESTABLISHED("vds.server.network.client.insecure-connections-established", Unit.CONNECTION, "Number of insecure (plaintext) connections established"),
    VDS_SERVER_NETWORK_SERVER_INSECURE_CONNECTIONS_ESTABLISHED("vds.server.network.server.insecure-connections-established", Unit.CONNECTION, "Number of insecure (plaintext) connections established"),
    VDS_SERVER_NETWORK_TLS_CONNECTIONS_BROKEN("vds.server.network.tls-connections-broken", Unit.CONNECTION, "Number of TLS connections broken due to failures during frame encoding or decoding"),
    VDS_SERVER_NETWORK_FAILED_TLS_CONFIG_RELOADS("vds.server.network.failed-tls-config-reloads", Unit.FAILURE, "Number of times background reloading of TLS config has failed"),

    VDS_BOUNCER_UNAVAILABLE_NODE_ABORTS("vds.bouncer.unavailable_node_aborts", Unit.OPERATION, "Number of operations that were aborted due to the node (or target bucket space) being unavailable"),
    VDS_CHANGEDBUCKETOWNERSHIPHANDLER_AVG_ABORT_PROCESSING_TIME("vds.changedbucketownershiphandler.avg_abort_processing_time", Unit.MILLISECOND, "Average time spent aborting operations for changed buckets"),
    VDS_CHANGEDBUCKETOWNERSHIPHANDLER_EXTERNAL_LOAD_OPS_ABORTED("vds.changedbucketownershiphandler.external_load_ops_aborted", Unit.OPERATION, "Number of outdated external load operations aborted"),
    VDS_CHANGEDBUCKETOWNERSHIPHANDLER_IDEAL_STATE_OPS_ABORTED("vds.changedbucketownershiphandler.ideal_state_ops_aborted", Unit.OPERATION, "Number of outdated ideal state operations aborted"),
    VDS_COMMUNICATION_BUCKET_SPACE_MAPPING_FAILURES("vds.communication.bucket_space_mapping_failures", Unit.OPERATION, "Number of messages that could not be resolved to a known bucket space"),
    VDS_COMMUNICATION_CONVERTFAILURES("vds.communication.convertfailures", Unit.OPERATION, "Number of messages that failed to get converted to storage API messages"),
    VDS_COMMUNICATION_EXCEPTIONMESSAGEPROCESSTIME("vds.communication.exceptionmessageprocesstime", Unit.MILLISECOND, "Time transport thread uses to process a single message that fails with an exception thrown into communication manager"),
    VDS_COMMUNICATION_MESSAGEPROCESSTIME("vds.communication.messageprocesstime", Unit.MILLISECOND, "Time transport thread uses to process a single message"),
    VDS_COMMUNICATION_MESSAGEQUEUE("vds.communication.messagequeue", Unit.ITEM, "Size of input message queue."),
    VDS_COMMUNICATION_SENDCOMMANDLATENCY("vds.communication.sendcommandlatency", Unit.MILLISECOND, "Average ms used to send commands to MBUS"),
    VDS_COMMUNICATION_SENDREPLYLATENCY("vds.communication.sendreplylatency", Unit.MILLISECOND, "Average ms used to send replies to MBUS"),
    VDS_COMMUNICATION_TOOLITTLEMEMORY("vds.communication.toolittlememory", Unit.OPERATION, "Number of messages failed due to too little memory available"),

    VDS_DATASTORED_BUCKET_SPACE_ACTIVE_BUCKETS("vds.datastored.bucket_space.active_buckets", Unit.BUCKET, "Number of active buckets in the bucket space"),
    VDS_DATASTORED_BUCKET_SPACE_BUCKET_DB_MEMORY_USAGE_ALLOCATED_BYTES("vds.datastored.bucket_space.bucket_db.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    VDS_DATASTORED_BUCKET_SPACE_BUCKET_DB_MEMORY_USAGE_DEAD_BYTES("vds.datastored.bucket_space.bucket_db.memory_usage.dead_bytes", Unit.BYTE, "The number of dead bytes (<= used_bytes)"),
    VDS_DATASTORED_BUCKET_SPACE_BUCKET_DB_MEMORY_USAGE_ONHOLD_BYTES("vds.datastored.bucket_space.bucket_db.memory_usage.onhold_bytes", Unit.BYTE, "The number of bytes on hold"),
    VDS_DATASTORED_BUCKET_SPACE_BUCKET_DB_MEMORY_USAGE_USED_BYTES("vds.datastored.bucket_space.bucket_db.memory_usage.used_bytes", Unit.BYTE, "The number of used bytes (<= allocated_bytes)"),
    VDS_DATASTORED_BUCKET_SPACE_BUCKETS_TOTAL("vds.datastored.bucket_space.buckets_total", Unit.BUCKET, "Total number buckets present in the bucket space (ready + not ready)"),
    VDS_DATASTORED_BUCKET_SPACE_BYTES("vds.datastored.bucket_space.bytes", Unit.BYTE, "Bytes stored across all documents in the bucket space"),
    VDS_DATASTORED_BUCKET_SPACE_DOCS("vds.datastored.bucket_space.docs", Unit.DOCUMENT, "Documents stored in the bucket space"),
    VDS_DATASTORED_BUCKET_SPACE_READY_BUCKETS("vds.datastored.bucket_space.ready_buckets", Unit.BUCKET, "Number of ready buckets in the bucket space"),
    VDS_DATASTORED_FULLBUCKETINFOLATENCY("vds.datastored.fullbucketinfolatency", Unit.MILLISECOND, "Amount of time spent to process a full bucket info request"),
    VDS_DATASTORED_FULLBUCKETINFOREQSIZE("vds.datastored.fullbucketinforeqsize", Unit.NODE, "Amount of distributors answered at once in full bucket info requests."),
    VDS_DATASTORED_SIMPLEBUCKETINFOREQSIZE("vds.datastored.simplebucketinforeqsize", Unit.BUCKET, "Amount of buckets returned in simple bucket info requests"),

    VDS_FILESTOR_ALLTHREADS_APPLYBUCKETDIFF_COUNT("vds.filestor.allthreads.applybucketdiff.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_APPLYBUCKETDIFF_FAILED("vds.filestor.allthreads.applybucketdiff.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_APPLYBUCKETDIFF_LATENCY("vds.filestor.allthreads.applybucketdiff.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_APPLYBUCKETDIFFREPLY("vds.filestor.allthreads.applybucketdiffreply", Unit.REQUEST, "Number of applybucketdiff replies that have been processed."),
    VDS_FILESTOR_ALLTHREADS_BUCKETFIXED("vds.filestor.allthreads.bucketfixed", Unit.BUCKET, "Number of times bucket has been fixed because of corruption"),
    VDS_FILESTOR_ALLTHREADS_BUCKETVERIFIED_COUNT("vds.filestor.allthreads.bucketverified.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_BUCKETVERIFIED_FAILED("vds.filestor.allthreads.bucketverified.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_BUCKETVERIFIED_LATENCY("vds.filestor.allthreads.bucketverified.latency", Unit.REQUEST, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_BYTESMERGED("vds.filestor.allthreads.bytesmerged", Unit.BYTE, "Total number of bytes merged into this node."),
    VDS_FILESTOR_ALLTHREADS_CREATEBUCKETS_COUNT("vds.filestor.allthreads.createbuckets.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_CREATEBUCKETS_FAILED("vds.filestor.allthreads.createbuckets.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_CREATEBUCKETS_LATENCY("vds.filestor.allthreads.createbuckets.latency", Unit.REQUEST, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_FAILEDOPERATIONS("vds.filestor.allthreads.failedoperations", Unit.OPERATION, "Number of operations throwing exceptions."),
    VDS_FILESTOR_ALLTHREADS_GETBUCKETDIFF_COUNT("vds.filestor.allthreads.getbucketdiff.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_GETBUCKETDIFF_FAILED("vds.filestor.allthreads.getbucketdiff.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_GETBUCKETDIFF_LATENCY("vds.filestor.allthreads.getbucketdiff.latency", Unit.REQUEST, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_GETBUCKETDIFFREPLY("vds.filestor.allthreads.getbucketdiffreply", Unit.REQUEST, "Number of getbucketdiff replies that have been processed."),
    VDS_FILESTOR_ALLTHREADS_INTERNALJOIN_COUNT("vds.filestor.allthreads.internaljoin.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_INTERNALJOIN_FAILED("vds.filestor.allthreads.internaljoin.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_INTERNALJOIN_LATENCY("vds.filestor.allthreads.internaljoin.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_MOVEDBUCKETS_COUNT("vds.filestor.allthreads.movedbuckets.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_MOVEDBUCKETS_FAILED("vds.filestor.allthreads.movedbuckets.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_MOVEDBUCKETS_LATENCY("vds.filestor.allthreads.movedbuckets.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_OPERATIONS("vds.filestor.allthreads.operations", Unit.OPERATION, "Number of operations processed."),

    VDS_FILESTOR_ALLTHREADS_READBUCKETINFO_COUNT("vds.filestor.allthreads.readbucketinfo.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_READBUCKETINFO_FAILED("vds.filestor.allthreads.readbucketinfo.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_READBUCKETINFO_LATENCY("vds.filestor.allthreads.readbucketinfo.latency", Unit.REQUEST, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_READBUCKETLIST_COUNT("vds.filestor.allthreads.readbucketlist.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_READBUCKETLIST_FAILED("vds.filestor.allthreads.readbucketlist.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_READBUCKETLIST_LATENCY("vds.filestor.allthreads.readbucketlist.latency", Unit.MILLISECOND, "Latency of successful requests."),

    VDS_FILESTOR_ALLTHREADS_RECHECKBUCKETINFO_COUNT("vds.filestor.allthreads.recheckbucketinfo.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_RECHECKBUCKETINFO_FAILED("vds.filestor.allthreads.recheckbucketinfo.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_RECHECKBUCKETINFO_LATENCY("vds.filestor.allthreads.recheckbucketinfo.latency", Unit.MILLISECOND, "Latency of successful requests."),

    VDS_FILESTOR_ALLTHREADS_REVERT_COUNT("vds.filestor.allthreads.revert.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_REVERT_FAILED("vds.filestor.allthreads.revert.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_REVERT_LATENCY("vds.filestor.allthreads.revert.latency", Unit.MILLISECOND, "Latency of successful requests."),
    VDS_FILESTOR_ALLTHREADS_REVERT_NOT_FOUND("vds.filestor.allthreads.revert.not_found", Unit.REQUEST, "Number of requests that could not be completed due to source document not found."),
    VDS_FILESTOR_ALLTHREADS_STAT_BUCKET_COUNT("vds.filestor.allthreads.stat_bucket.count", Unit.REQUEST, "Number of requests processed."),
    VDS_FILESTOR_ALLTHREADS_STAT_BUCKET_FAILED("vds.filestor.allthreads.stat_bucket.failed", Unit.REQUEST, "Number of failed requests."),
    VDS_FILESTOR_ALLTHREADS_STAT_BUCKET_LATENCY("vds.filestor.allthreads.stat_bucket.latency", Unit.REQUEST, "Latency of successful requests."),
    VDS_FILESTOR_BUCKET_DB_INIT_LATENCY("vds.filestor.bucket_db_init_latency", Unit.MILLISECOND, "Time taken (in ms) to initialize bucket databases with information from the persistence provider"),
    VDS_FILESTOR_DIRECTORYEVENTS("vds.filestor.directoryevents", Unit.OPERATION, "Number of directory events received."),
    VDS_FILESTOR_DISKEVENTS("vds.filestor.diskevents", Unit.OPERATION, "Number of disk events received."),
    VDS_FILESTOR_PARTITIONEVENTS("vds.filestor.partitionevents", Unit.OPERATION, "Number of partition events received."),
    VDS_FILESTOR_PENDINGMERGE("vds.filestor.pendingmerge", Unit.BUCKET, "Number of buckets currently being merged."),
    VDS_FILESTOR_WAITINGFORLOCKRATE("vds.filestor.waitingforlockrate", Unit.OPERATION, "Amount of times a filestor thread has needed to wait for lock to take next message in queue."),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_ABORTED("vds.mergethrottler.mergechains.failures.aborted", Unit.OPERATION, "The number of merges that failed because the storage node was (most likely) shutting down"),
    VDS_MERGETHROTTLER_MERGECHAINS_FAILURES_BUCKETNOTFOUND("vds.mergethrottler.mergechains.failures.bucketnotfound", Unit.OPERATION, "The number of operations that failed because the bucket did not exist"),
    VDS_SERVER_MEMORYUSAGE("vds.server.memoryusage", Unit.BYTE, "Amount of memory used by the storage subsystem"),
    VDS_SERVER_MEMORYUSAGE_VISITING("vds.server.memoryusage_visiting", Unit.BYTE, "Message use from visiting"),
    VDS_SERVER_MESSAGE_MEMORY_USE_HIGHPRI("vds.server.message_memory_use.highpri", Unit.BYTE, "Message use from high priority storage messages"),
    VDS_SERVER_MESSAGE_MEMORY_USE_LOWPRI("vds.server.message_memory_use.lowpri", Unit.BYTE, "Message use from low priority storage messages"),
    VDS_SERVER_MESSAGE_MEMORY_USE_NORMALPRI("vds.server.message_memory_use.normalpri", Unit.BYTE, "Message use from normal priority storage messages"),
    VDS_SERVER_MESSAGE_MEMORY_USE_TOTAL("vds.server.message_memory_use.total", Unit.BYTE, "Message use from storage messages"),
    VDS_SERVER_MESSAGE_MEMORY_USE_VERYHIGHPRI("vds.server.message_memory_use.veryhighpri", Unit.BYTE, "Message use from very high priority storage messages"),
    VDS_STATE_MANAGER_INVOKE_STATE_LISTENERS_LATENCY("vds.state_manager.invoke_state_listeners_latency", Unit.MILLISECOND, "Time spent (in ms) propagating state changes to internal state listeners"),
    VDS_VISITOR_CV_QUEUEEVICTEDWAITTIME("vds.visitor.cv_queueevictedwaittime", Unit.MILLISECOND, "Milliseconds waiting in create visitor queue, for visitors that was evicted from queue due to higher priority visitors coming"),
    VDS_VISITOR_CV_QUEUEFULL("vds.visitor.cv_queuefull", Unit.OPERATION, "Number of create visitor messages failed as queue is full"),
    VDS_VISITOR_CV_QUEUESIZE("vds.visitor.cv_queuesize", Unit.ITEM, "Size of create visitor queue"),
    VDS_VISITOR_CV_QUEUETIMEOUTWAITTIME("vds.visitor.cv_queuetimeoutwaittime", Unit.MILLISECOND, "Milliseconds waiting in create visitor queue, for visitors that timed out while in the visitor quueue"),
    VDS_VISITOR_CV_QUEUEWAITTIME("vds.visitor.cv_queuewaittime", Unit.MILLISECOND, "Milliseconds waiting in create visitor queue, for visitors that was added to visitor queue but scheduled later"),
    VDS_VISITOR_CV_SKIPQUEUE("vds.visitor.cv_skipqueue", Unit.OPERATION, "Number of times we could skip queue as we had free visitor spots"),

    // C++ capability metrics
    VDS_SERVER_NETWORK_RPC_CAPABILITY_CHECKS_FAILED("vds.server.network.rpc-capability-checks-failed", Unit.FAILURE, "Number of RPC operations that failed to due one or more missing capabilities"),
    VDS_SERVER_NETWORK_STATUS_CAPABILITY_CHECKS_FAILED("vds.server.network.status-capability-checks-failed", Unit.FAILURE, "Number of status page operations that failed to due one or more missing capabilities"),

    // C++ Fnet metrics
    VDS_SERVER_FNET_NUM_CONNECTIONS("vds.server.fnet.num-connections", Unit.CONNECTION, "Total number of connection objects");


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
