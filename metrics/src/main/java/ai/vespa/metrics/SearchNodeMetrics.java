package ai.vespa.metrics;

/**
 * @author gjoranv
 */
public enum SearchNodeMetrics implements VespaMetrics {

    CONTENT_PROTON_CONFIG_GENERATION("content.proton.config.generation", Unit.VERSION, "The oldest config generation used by this search node"),

    CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_TOTAL("content.proton.documentdb.documents.total", Unit.DOCUMENT, "The total number of documents in this documents db (ready + not-ready)"),
    CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_READY("content.proton.documentdb.documents.ready", Unit.DOCUMENT, "The number of ready documents in this document db"),
    CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_ACTIVE("content.proton.documentdb.documents.active", Unit.DOCUMENT, "The number of active / searchable documents in this document db"),
    CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_REMOVED("content.proton.documentdb.documents.removed", Unit.DOCUMENT, "The number of removed documents in this document db"),

    CONTENT_PROTON_DOCUMENTDB_INDEX_DOCS_IN_MEMORY("content.proton.documentdb.index.docs_in_memory", Unit.DOCUMENT, "Number of documents in memory index"),
    CONTENT_PROTON_DOCUMENTDB_DISK_USAGE("content.proton.documentdb.disk_usage", Unit.BYTE, "The total disk usage (in bytes) for this document db"),
    CONTENT_PROTON_DOCUMENTDB_MEMORY_USAGE_ALLOCATED_BYTES("content.proton.documentdb.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    CONTENT_PROTON_DOCUMENTDB_MEMORY_USAGE_DEAD_BYTES("content.proton.documentdb.memory_usage.dead_bytes", Unit.BYTE, "The number of dead bytes (<= used_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_MEMORY_USAGE_ONHOLD_BYTES("content.proton.documentdb.memory_usage.onhold_bytes", Unit.BYTE, "The number of bytes on hold"),
    CONTENT_PROTON_DOCUMENTDB_MEMORY_USAGE_USED_BYTES("content.proton.documentdb.memory_usage.used_bytes", Unit.BYTE, "The number of used bytes (<= allocated_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_HEART_BEAT_AGE("content.proton.documentdb.heart_beat_age", Unit.SECOND, "How long ago (in seconds) heart beat maintenace job was run"),
    CONTENT_PROTON_DOCSUM_COUNT("content.proton.docsum.count", Unit.REQUEST, "Docsum requests handled"),
    CONTENT_PROTON_DOCSUM_DOCS("content.proton.docsum.docs", Unit.DOCUMENT, "Total docsums returned"),
    CONTENT_PROTON_DOCSUM_LATENCY("content.proton.docsum.latency", Unit.MILLISECOND, "Docsum request latency"),

    // Search protocol
    CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_LATENCY("content.proton.search_protocol.query.latency", Unit.SECOND, "Query request latency (seconds)"),
    CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_REQUEST_SIZE("content.proton.search_protocol.query.request_size", Unit.BYTE, "Query request size (network bytes)"),
    CONTENT_PROTON_SEARCH_PROTOCOL_QUERY_REPLY_SIZE("content.proton.search_protocol.query.reply_size", Unit.BYTE, "Query reply size (network bytes)"),
    CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_LATENCY("content.proton.search_protocol.docsum.latency", Unit.SECOND, "Docsum request latency (seconds)"),
    CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REQUEST_SIZE("content.proton.search_protocol.docsum.request_size", Unit.BYTE, "Docsum request size (network bytes)"),
    CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REPLY_SIZE("content.proton.search_protocol.docsum.reply_size", Unit.BYTE, "Docsum reply size (network bytes)"),
    CONTENT_PROTON_SEARCH_PROTOCOL_DOCSUM_REQUESTED_DOCUMENTS("content.proton.search_protocol.docsum.requested_documents", Unit.DOCUMENT, "Total requested document summaries"),

    // Executors shared between all document dbs
    CONTENT_PROTON_EXECUTOR_PROTON_QUEUESIZE("content.proton.executor.proton.queuesize", Unit.TASK, "Size of executor proton task queue"),
    CONTENT_PROTON_EXECUTOR_PROTON_ACCEPTED("content.proton.executor.proton.accepted", Unit.TASK, "Number of executor proton accepted tasks"),
    CONTENT_PROTON_EXECUTOR_PROTON_WAKEUPS("content.proton.executor.proton.wakeups", Unit.WAKEUP, "Number of times a executor proton worker thread has been woken up"),
    CONTENT_PROTON_EXECUTOR_PROTON_UTILIZATION("content.proton.executor.proton.utilization", Unit.FRACTION, "Ratio of time the executor proton worker threads has been active"),
    CONTENT_PROTON_EXECUTOR_PROTON_REJECTED("content.proton.executor.proton.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_EXECUTOR_FLUSH_QUEUESIZE("content.proton.executor.flush.queuesize", Unit.TASK, "Size of executor flush task queue"),
    CONTENT_PROTON_EXECUTOR_FLUSH_ACCEPTED("content.proton.executor.flush.accepted", Unit.TASK, "Number of accepted executor flush tasks"),
    CONTENT_PROTON_EXECUTOR_FLUSH_WAKEUPS("content.proton.executor.flush.wakeups", Unit.WAKEUP, "Number of times a executor flush worker thread has been woken up"),
    CONTENT_PROTON_EXECUTOR_FLUSH_UTILIZATION("content.proton.executor.flush.utilization", Unit.FRACTION, "Ratio of time the executor flush worker threads has been active"),
    CONTENT_PROTON_EXECUTOR_FLUSH_REJECTED("content.proton.executor.flush.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_EXECUTOR_MATCH_QUEUESIZE("content.proton.executor.match.queuesize", Unit.TASK, "Size of executor match task queue"),
    CONTENT_PROTON_EXECUTOR_MATCH_ACCEPTED("content.proton.executor.match.accepted", Unit.TASK, "Number of accepted executor match tasks"),
    CONTENT_PROTON_EXECUTOR_MATCH_WAKEUPS("content.proton.executor.match.wakeups", Unit.WAKEUP, "Number of times a executor match worker thread has been woken up"),
    CONTENT_PROTON_EXECUTOR_MATCH_UTILIZATION("content.proton.executor.match.utilization", Unit.FRACTION, "Ratio of time the executor match worker threads has been active"),
    CONTENT_PROTON_EXECUTOR_MATCH_REJECTED("content.proton.executor.match.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_EXECUTOR_DOCSUM_QUEUESIZE("content.proton.executor.docsum.queuesize", Unit.TASK, "Size of executor docsum task queue"),
    CONTENT_PROTON_EXECUTOR_DOCSUM_ACCEPTED("content.proton.executor.docsum.accepted", Unit.TASK, "Number of executor accepted docsum tasks"),
    CONTENT_PROTON_EXECUTOR_DOCSUM_WAKEUPS("content.proton.executor.docsum.wakeups", Unit.WAKEUP, "Number of times a executor docsum worker thread has been woken up"),
    CONTENT_PROTON_EXECUTOR_DOCSUM_UTILIZATION("content.proton.executor.docsum.utilization", Unit.FRACTION, "Ratio of time the executor docsum worker threads has been active"),
    CONTENT_PROTON_EXECUTOR_DOCSUM_REJECTED("content.proton.executor.docsum.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_EXECUTOR_SHARED_QUEUESIZE("content.proton.executor.shared.queuesize", Unit.TASK, "Size of executor shared task queue"),
    CONTENT_PROTON_EXECUTOR_SHARED_ACCEPTED("content.proton.executor.shared.accepted", Unit.TASK, "Number of executor shared accepted tasks"),
    CONTENT_PROTON_EXECUTOR_SHARED_WAKEUPS("content.proton.executor.shared.wakeups", Unit.WAKEUP, "Number of times a executor shared worker thread has been woken up"),
    CONTENT_PROTON_EXECUTOR_SHARED_UTILIZATION("content.proton.executor.shared.utilization", Unit.FRACTION, "Ratio of time the executor shared worker threads has been active"),
    CONTENT_PROTON_EXECUTOR_SHARED_REJECTED("content.proton.executor.shared.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_EXECUTOR_WARMUP_QUEUESIZE("content.proton.executor.warmup.queuesize", Unit.TASK, "Size of executor warmup task queue"),
    CONTENT_PROTON_EXECUTOR_WARMUP_ACCEPTED("content.proton.executor.warmup.accepted", Unit.TASK, "Number of accepted executor warmup tasks"),
    CONTENT_PROTON_EXECUTOR_WARMUP_WAKEUPS("content.proton.executor.warmup.wakeups", Unit.WAKEUP, "Number of times a warmup executor worker thread has been woken up"),
    CONTENT_PROTON_EXECUTOR_WARMUP_UTILIZATION("content.proton.executor.warmup.utilization", Unit.FRACTION, "Ratio of time the executor warmup worker threads has been active"),
    CONTENT_PROTON_EXECUTOR_WARMUP_REJECTED("content.proton.executor.warmup.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_EXECUTOR_FIELD_WRITER_QUEUESIZE("content.proton.executor.field_writer.queuesize", Unit.TASK, "Size of executor field writer task queue"),
    CONTENT_PROTON_EXECUTOR_FIELD_WRITER_ACCEPTED("content.proton.executor.field_writer.accepted", Unit.TASK, "Number of accepted executor field writer tasks"),
    CONTENT_PROTON_EXECUTOR_FIELD_WRITER_WAKEUPS("content.proton.executor.field_writer.wakeups", Unit.WAKEUP, "Number of times a executor field writer worker thread has been woken up"),
    CONTENT_PROTON_EXECUTOR_FIELD_WRITER_UTILIZATION("content.proton.executor.field_writer.utilization", Unit.FRACTION, "Ratio of time the executor fieldwriter worker threads has been active"),
    CONTENT_PROTON_EXECUTOR_FIELD_WRITER_REJECTED("content.proton.executor.field_writer.rejected", Unit.TASK, "Number of rejected tasks"),

    // jobs
    CONTENT_PROTON_DOCUMENTDB_JOB_TOTAL("content.proton.documentdb.job.total", Unit.FRACTION, "The job load average total of all job metrics"),
    CONTENT_PROTON_DOCUMENTDB_JOB_ATTRIBUTE_FLUSH("content.proton.documentdb.job.attribute_flush", Unit.FRACTION, "Flushing of attribute vector(s) to disk"),
    CONTENT_PROTON_DOCUMENTDB_JOB_MEMORY_INDEX_FLUSH("content.proton.documentdb.job.memory_index_flush", Unit.FRACTION, "Flushing of memory index to disk"),
    CONTENT_PROTON_DOCUMENTDB_JOB_DISK_INDEX_FUSION("content.proton.documentdb.job.disk_index_fusion", Unit.FRACTION, "Fusion of disk indexes"),
    CONTENT_PROTON_DOCUMENTDB_JOB_DOCUMENT_STORE_FLUSH("content.proton.documentdb.job.document_store_flush", Unit.FRACTION, "Flushing of document store to disk"),
    CONTENT_PROTON_DOCUMENTDB_JOB_DOCUMENT_STORE_COMPACT("content.proton.documentdb.job.document_store_compact", Unit.FRACTION, "Compaction of document store on disk"),
    CONTENT_PROTON_DOCUMENTDB_JOB_BUCKET_MOVE("content.proton.documentdb.job.bucket_move", Unit.FRACTION, "Moving of buckets between 'ready' and 'notready' sub databases"),
    CONTENT_PROTON_DOCUMENTDB_JOB_LID_SPACE_COMPACT("content.proton.documentdb.job.lid_space_compact", Unit.FRACTION, "Compaction of lid space in document meta store and attribute vectors"),
    CONTENT_PROTON_DOCUMENTDB_JOB_REMOVED_DOCUMENTS_PRUNE("content.proton.documentdb.job.removed_documents_prune", Unit.FRACTION, "Pruning of removed documents in 'removed' sub database"),

    // Threading service (per document db)
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_QUEUESIZE("content.proton.documentdb.threading_service.master.queuesize", Unit.TASK, "Size of threading service master task queue"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_ACCEPTED("content.proton.documentdb.threading_service.master.accepted", Unit.TASK, "Number of accepted threading service master tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_WAKEUPS("content.proton.documentdb.threading_service.master.wakeups", Unit.WAKEUP, "Number of times a threading service master worker thread has been woken up"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_UTILIZATION("content.proton.documentdb.threading_service.master.utilization", Unit.FRACTION, "Ratio of time the threading service master worker threads has been active"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_MASTER_REJECTED("content.proton.documentdb.threading_service.master.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_QUEUESIZE("content.proton.documentdb.threading_service.index.queuesize", Unit.TASK, "Size of threading service index task queue"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_ACCEPTED("content.proton.documentdb.threading_service.index.accepted", Unit.TASK, "Number of accepted threading service index tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_WAKEUPS("content.proton.documentdb.threading_service.index.wakeups", Unit.WAKEUP, "Number of times a threading service index worker thread has been woken up"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_UTILIZATION("content.proton.documentdb.threading_service.index.utilization", Unit.FRACTION, "Ratio of time the threading service index worker threads has been active"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_REJECTED("content.proton.documentdb.threading_service.index.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_QUEUESIZE("content.proton.documentdb.threading_service.summary.queuesize", Unit.TASK, "Size of threading service summary task queue"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_ACCEPTED("content.proton.documentdb.threading_service.summary.accepted", Unit.TASK, "Number of accepted threading service summary tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_WAKEUPS("content.proton.documentdb.threading_service.summary.wakeups", Unit.WAKEUP, "Number of times a threading service summary worker thread has been woken up"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_UTILIZATION("content.proton.documentdb.threading_service.summary.utilization", Unit.FRACTION, "Ratio of time the threading service summary worker threads has been active"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_SUMMARY_REJECTED("content.proton.documentdb.threading_service.summary.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_ATTRIBUTE_FIELD_WRITER_ACCEPTED("content.proton.documentdb.threading_service.attribute_field_writer.accepted", Unit.TASK, "Number of accepted tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_ATTRIBUTE_FIELD_WRITER_QUEUESIZE("content.proton.documentdb.threading_service.attribute_field_writer.queuesize", Unit.TASK, "Size of task queue"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_ATTRIBUTE_FIELD_WRITER_REJECTED("content.proton.documentdb.threading_service.attribute_field_writer.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_ATTRIBUTE_FIELD_WRITER_UTILIZATION("content.proton.documentdb.threading_service.attribute_field_writer.utilization", Unit.FRACTION, "Ratio of time the worker threads has been active"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_ATTRIBUTE_FIELD_WRITER_WAKEUPS("content.proton.documentdb.threading_service.attribute_field_writer.wakeups", Unit.WAKEUP, "Number of times a worker thread has been woken up"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_INVERTER_ACCEPTED("content.proton.documentdb.threading_service.index_field_inverter.accepted", Unit.TASK, "Number of accepted tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_INVERTER_QUEUESIZE("content.proton.documentdb.threading_service.index_field_inverter.queuesize", Unit.TASK, "Size of task queue"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_INVERTER_REJECTED("content.proton.documentdb.threading_service.index_field_inverter.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_INVERTER_UTILIZATION("content.proton.documentdb.threading_service.index_field_inverter.utilization", Unit.FRACTION, "Ratio of time the worker threads has been active"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_INVERTER_WAKEUPS("content.proton.documentdb.threading_service.index_field_inverter.wakeups", Unit.WAKEUP, "Number of times a worker thread has been woken up"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_WRITER_ACCEPTED("content.proton.documentdb.threading_service.index_field_writer.accepted", Unit.TASK, "Number of accepted tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_WRITER_QUEUESIZE("content.proton.documentdb.threading_service.index_field_writer.queuesize", Unit.TASK, "Size of task queue"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_WRITER_REJECTED("content.proton.documentdb.threading_service.index_field_writer.rejected", Unit.TASK, "Number of rejected tasks"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_WRITER_UTILIZATION("content.proton.documentdb.threading_service.index_field_writer.utilization", Unit.FRACTION, "Ratio of time the worker threads has been active"),
    CONTENT_PROTON_DOCUMENTDB_THREADING_SERVICE_INDEX_FIELD_WRITER_WAKEUPS("content.proton.documentdb.threading_service.index_field_writer.wakeups", Unit.WAKEUP, "Number of times a worker thread has been woken up"),

    // lid space
    CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_LID_BLOAT_FACTOR("content.proton.documentdb.ready.lid_space.lid_bloat_factor", Unit.FRACTION, "The bloat factor of this lid space, indicating the total amount of holes in the allocated lid space ((lid_limit - used_lids) / lid_limit)"),
    CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_LID_FRAGMENTATION_FACTOR("content.proton.documentdb.ready.lid_space.lid_fragmentation_factor", Unit.FRACTION, "The fragmentation factor of this lid space, indicating the amount of holes in the currently used part of the lid space ((highest_used_lid - used_lids) / highest_used_lid)"),
    CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_LID_LIMIT("content.proton.documentdb.ready.lid_space.lid_limit", Unit.DOCUMENTID, "The size of the allocated lid space"),
    CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_HIGHEST_USED_LID("content.proton.documentdb.ready.lid_space.highest_used_lid", Unit.DOCUMENTID, "The highest used lid"),
    CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_USED_LIDS("content.proton.documentdb.ready.lid_space.used_lids", Unit.DOCUMENTID, "The number of lids used"),
    CONTENT_PROTON_DOCUMENTDB_READY_LID_SPACE_LOWEST_FREE_LID("content.proton.documentdb.ready.lid_space.lowest_free_lid", Unit.DOCUMENTID, "The lowest free local document id"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_LID_BLOAT_FACTOR("content.proton.documentdb.notready.lid_space.lid_bloat_factor", Unit.FRACTION, "The bloat factor of this lid space, indicating the total amount of holes in the allocated lid space ((lid_limit - used_lids) / lid_limit)"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_LID_FRAGMENTATION_FACTOR("content.proton.documentdb.notready.lid_space.lid_fragmentation_factor", Unit.FRACTION, "The fragmentation factor of this lid space, indicating the amount of holes in the currently used part of the lid space ((highest_used_lid - used_lids) / highest_used_lid)"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_LID_LIMIT("content.proton.documentdb.notready.lid_space.lid_limit", Unit.DOCUMENTID, "The size of the allocated lid space"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_HIGHEST_USED_LID("content.proton.documentdb.notready.lid_space.highest_used_lid", Unit.DOCUMENTID, "The highest used lid"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_USED_LIDS("content.proton.documentdb.notready.lid_space.used_lids", Unit.DOCUMENTID, "The number of lids used"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_LID_SPACE_LOWEST_FREE_LID("content.proton.documentdb.notready.lid_space.lowest_free_lid", Unit.DOCUMENTID, "The lowest free local document id"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_LID_BLOAT_FACTOR("content.proton.documentdb.removed.lid_space.lid_bloat_factor", Unit.FRACTION, "The bloat factor of this lid space, indicating the total amount of holes in the allocated lid space ((lid_limit - used_lids) / lid_limit)"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_LID_FRAGMENTATION_FACTOR("content.proton.documentdb.removed.lid_space.lid_fragmentation_factor", Unit.FRACTION, "The fragmentation factor of this lid space, indicating the amount of holes in the currently used part of the lid space ((highest_used_lid - used_lids) / highest_used_lid)"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_LID_LIMIT("content.proton.documentdb.removed.lid_space.lid_limit", Unit.DOCUMENTID, "The size of the allocated lid space"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_HIGHEST_USED_LID("content.proton.documentdb.removed.lid_space.highest_used_lid", Unit.DOCUMENTID, "The highest used lid"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_USED_LIDS("content.proton.documentdb.removed.lid_space.used_lids", Unit.DOCUMENTID, "The number of lids used"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_LID_SPACE_LOWEST_FREE_LID("content.proton.documentdb.removed.lid_space.lowest_free_lid", Unit.DOCUMENTID, "The lowest free local document id"),

    // bucket move
    CONTENT_PROTON_DOCUMENTDB_BUCKET_MOVE_BUCKETS_PENDING("content.proton.documentdb.bucket_move.buckets_pending", Unit.BUCKET, "The number of buckets left to move"),

    // resource usage
    CONTENT_PROTON_RESOURCE_USAGE_DISK("content.proton.resource_usage.disk", Unit.FRACTION, "The relative amount of disk used by this content node (transient usage not included, value in the range [0, 1]). Same value as reported to the cluster controller"),
    CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_TOTAL("content.proton.resource_usage.disk_usage.total", Unit.FRACTION, "The total relative amount of disk used by this content node (value in the range [0, 1])"),
    CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_TOTAL_UTILIZATION("content.proton.resource_usage.disk_usage.total_utilization", Unit.FRACTION, "The relative amount of disk used compared to the content node disk resource limit"),
    CONTENT_PROTON_RESOURCE_USAGE_DISK_USAGE_TRANSIENT("content.proton.resource_usage.disk_usage.transient", Unit.FRACTION, "The relative amount of transient disk used by this content node (value in the range [0, 1])"),
    CONTENT_PROTON_RESOURCE_USAGE_MEMORY("content.proton.resource_usage.memory", Unit.FRACTION, "The relative amount of memory used by this content node (transient usage not included, value in the range [0, 1]). Same value as reported to the cluster controller"),
    CONTENT_PROTON_RESOURCE_USAGE_MEMORY_USAGE_TOTAL("content.proton.resource_usage.memory_usage.total", Unit.FRACTION, "The total relative amount of memory used by this content node (value in the range [0, 1])"),
    CONTENT_PROTON_RESOURCE_USAGE_MEMORY_USAGE_TOTAL_UTILIZATION("content.proton.resource_usage.memory_usage.total_utilization", Unit.FRACTION, "The relative amount of memory used compared to the content node memory resource limit"),
    CONTENT_PROTON_RESOURCE_USAGE_MEMORY_USAGE_TRANSIENT("content.proton.resource_usage.memory_usage.transient", Unit.FRACTION, "The relative amount of transient memory used by this content node (value in the range [0, 1])"),
    CONTENT_PROTON_RESOURCE_USAGE_MEMORY_MAPPINGS("content.proton.resource_usage.memory_mappings", Unit.FILE, "The number of memory mapped files"),
    CONTENT_PROTON_RESOURCE_USAGE_OPEN_FILE_DESCRIPTORS("content.proton.resource_usage.open_file_descriptors", Unit.FILE, "The number of open files"),
    CONTENT_PROTON_RESOURCE_USAGE_FEEDING_BLOCKED("content.proton.resource_usage.feeding_blocked", Unit.BINARY, "Whether feeding is blocked due to resource limits being reached (value is either 0 or 1)"),
    CONTENT_PROTON_RESOURCE_USAGE_MALLOC_ARENA("content.proton.resource_usage.malloc_arena", Unit.BYTE, "Size of malloc arena"),
    CONTENT_PROTON_DOCUMENTDB_ATTRIBUTE_RESOURCE_USAGE_ADDRESS_SPACE("content.proton.documentdb.attribute.resource_usage.address_space", Unit.FRACTION, "The max relative address space used among components in all attribute vectors in this document db (value in the range [0, 1])"),
    CONTENT_PROTON_DOCUMENTDB_ATTRIBUTE_RESOURCE_USAGE_FEEDING_BLOCKED("content.proton.documentdb.attribute.resource_usage.feeding_blocked", Unit.BINARY, "Whether feeding is blocked due to attribute resource limits being reached (value is either 0 or 1)"),
    CONTENT_PROTON_DOCUMENTDB_ATTRIBUTE_MEMORY_USAGE_ALLOCATED_BYTES("content.proton.documentdb.attribute.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    CONTENT_PROTON_DOCUMENTDB_ATTRIBUTE_MEMORY_USAGE_DEAD_BYTES("content.proton.documentdb.attribute.memory_usage.dead_bytes", Unit.BYTE, "The number of dead bytes (<= used_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_ATTRIBUTE_MEMORY_USAGE_ONHOLD_BYTES("content.proton.documentdb.attribute.memory_usage.onhold_bytes", Unit.BYTE, "The number of bytes on hold"),
    CONTENT_PROTON_DOCUMENTDB_ATTRIBUTE_MEMORY_USAGE_USED_BYTES("content.proton.documentdb.attribute.memory_usage.used_bytes", Unit.BYTE, "The number of used bytes (<= allocated_bytes)"),

    // CPU util
    CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_SETUP("content.proton.resource_usage.cpu_util.setup", Unit.FRACTION, "cpu used by system init and (re-)configuration"),
    CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_READ("content.proton.resource_usage.cpu_util.read", Unit.FRACTION, "cpu used by reading data from the system"),
    CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_WRITE("content.proton.resource_usage.cpu_util.write", Unit.FRACTION, "cpu used by writing data to the system"),
    CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_COMPACT("content.proton.resource_usage.cpu_util.compact", Unit.FRACTION, "cpu used by internal data re-structuring"),
    CONTENT_PROTON_RESOURCE_USAGE_CPU_UTIL_OTHER("content.proton.resource_usage.cpu_util.other", Unit.FRACTION, "cpu used by work not classified as a specific category"),

    // transaction log
    CONTENT_PROTON_TRANSACTIONLOG_ENTRIES("content.proton.transactionlog.entries", Unit.RECORD, "The current number of entries in the transaction log"),
    CONTENT_PROTON_TRANSACTIONLOG_DISK_USAGE("content.proton.transactionlog.disk_usage", Unit.BYTE, "The disk usage (in bytes) of the transaction log"),
    CONTENT_PROTON_TRANSACTIONLOG_REPLAY_TIME("content.proton.transactionlog.replay_time", Unit.SECOND, "The replay time (in seconds) of the transaction log during start-up"),

    // document store
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_DISK_USAGE("content.proton.documentdb.ready.document_store.disk_usage", Unit.BYTE, "Disk space usage in bytes"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_DISK_BLOAT("content.proton.documentdb.ready.document_store.disk_bloat", Unit.BYTE, "Disk space bloat in bytes"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MAX_BUCKET_SPREAD("content.proton.documentdb.ready.document_store.max_bucket_spread", Unit.FRACTION, "Max bucket spread in underlying files (sum(unique buckets in each chunk)/unique buckets in file)"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MEMORY_USAGE_ALLOCATED_BYTES("content.proton.documentdb.ready.document_store.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MEMORY_USAGE_USED_BYTES("content.proton.documentdb.ready.document_store.memory_usage.used_bytes", Unit.BYTE, "The number of used bytes (<= allocated_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MEMORY_USAGE_DEAD_BYTES("content.proton.documentdb.ready.document_store.memory_usage.dead_bytes", Unit.BYTE, "The number of dead bytes (<= used_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_MEMORY_USAGE_ONHOLD_BYTES("content.proton.documentdb.ready.document_store.memory_usage.onhold_bytes", Unit.BYTE, "The number of bytes on hold"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_DISK_USAGE("content.proton.documentdb.notready.document_store.disk_usage", Unit.BYTE, "Disk space usage in bytes"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_DISK_BLOAT("content.proton.documentdb.notready.document_store.disk_bloat", Unit.BYTE, "Disk space bloat in bytes"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MAX_BUCKET_SPREAD("content.proton.documentdb.notready.document_store.max_bucket_spread", Unit.FRACTION, "Max bucket spread in underlying files (sum(unique buckets in each chunk)/unique buckets in file)"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MEMORY_USAGE_ALLOCATED_BYTES("content.proton.documentdb.notready.document_store.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MEMORY_USAGE_USED_BYTES("content.proton.documentdb.notready.document_store.memory_usage.used_bytes", Unit.BYTE, "The number of used bytes (<= allocated_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MEMORY_USAGE_DEAD_BYTES("content.proton.documentdb.notready.document_store.memory_usage.dead_bytes", Unit.BYTE, "The number of dead bytes (<= used_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_MEMORY_USAGE_ONHOLD_BYTES("content.proton.documentdb.notready.document_store.memory_usage.onhold_bytes", Unit.BYTE, "The number of bytes on hold"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_DISK_USAGE("content.proton.documentdb.removed.document_store.disk_usage", Unit.BYTE, "Disk space usage in bytes"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_DISK_BLOAT("content.proton.documentdb.removed.document_store.disk_bloat", Unit.BYTE, "Disk space bloat in bytes"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MAX_BUCKET_SPREAD("content.proton.documentdb.removed.document_store.max_bucket_spread", Unit.FRACTION, "Max bucket spread in underlying files (sum(unique buckets in each chunk)/unique buckets in file)"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MEMORY_USAGE_ALLOCATED_BYTES("content.proton.documentdb.removed.document_store.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MEMORY_USAGE_USED_BYTES("content.proton.documentdb.removed.document_store.memory_usage.used_bytes", Unit.BYTE, "The number of used bytes (<= allocated_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MEMORY_USAGE_DEAD_BYTES("content.proton.documentdb.removed.document_store.memory_usage.dead_bytes", Unit.BYTE, "The number of dead bytes (<= used_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_MEMORY_USAGE_ONHOLD_BYTES("content.proton.documentdb.removed.document_store.memory_usage.onhold_bytes", Unit.BYTE, "The number of bytes on hold"),

    // document store cache
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_CACHE_ELEMENTS("content.proton.documentdb.ready.document_store.cache.elements", Unit.ITEM, "Number of elements in the cache"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_CACHE_MEMORY_USAGE("content.proton.documentdb.ready.document_store.cache.memory_usage", Unit.BYTE, "Memory usage of the cache (in bytes)"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_CACHE_HIT_RATE("content.proton.documentdb.ready.document_store.cache.hit_rate", Unit.FRACTION, "Rate of hits in the cache compared to number of lookups"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_CACHE_LOOKUPS("content.proton.documentdb.ready.document_store.cache.lookups", Unit.OPERATION, "Number of lookups in the cache (hits + misses)"),
    CONTENT_PROTON_DOCUMENTDB_READY_DOCUMENT_STORE_CACHE_INVALIDATIONS("content.proton.documentdb.ready.document_store.cache.invalidations", Unit.OPERATION, "Number of invalidations (erased elements) in the cache. "),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_CACHE_ELEMENTS("content.proton.documentdb.notready.document_store.cache.elements", Unit.ITEM, "Number of elements in the cache"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_CACHE_MEMORY_USAGE("content.proton.documentdb.notready.document_store.cache.memory_usage", Unit.BYTE, "Memory usage of the cache (in bytes)"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_CACHE_HIT_RATE("content.proton.documentdb.notready.document_store.cache.hit_rate", Unit.FRACTION, "Rate of hits in the cache compared to number of lookups"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_CACHE_LOOKUPS("content.proton.documentdb.notready.document_store.cache.lookups", Unit.OPERATION, "Number of lookups in the cache (hits + misses)"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_DOCUMENT_STORE_CACHE_INVALIDATIONS("content.proton.documentdb.notready.document_store.cache.invalidations", Unit.OPERATION, "Number of invalidations (erased elements) in the cache. "),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_CACHE_ELEMENTS("content.proton.documentdb.removed.document_store.cache.elements", Unit.ITEM, "Number of elements in the cache"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_CACHE_HIT_RATE("content.proton.documentdb.removed.document_store.cache.hit_rate", Unit.FRACTION, "Rate of hits in the cache compared to number of lookups"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_CACHE_INVALIDATIONS("content.proton.documentdb.removed.document_store.cache.invalidations", Unit.ITEM, "Number of invalidations (erased elements) in the cache. "),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_CACHE_LOOKUPS("content.proton.documentdb.removed.document_store.cache.lookups", Unit.OPERATION, "Number of lookups in the cache (hits + misses)"),
    CONTENT_PROTON_DOCUMENTDB_REMOVED_DOCUMENT_STORE_CACHE_MEMORY_USAGE("content.proton.documentdb.removed.document_store.cache.memory_usage", Unit.BYTE, "Memory usage of the cache (in bytes)"),

    // attribute
    CONTENT_PROTON_DOCUMENTDB_READY_ATTRIBUTE_MEMORY_USAGE_ALLOCATED_BYTES("content.proton.documentdb.ready.attribute.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    CONTENT_PROTON_DOCUMENTDB_READY_ATTRIBUTE_MEMORY_USAGE_USED_BYTES("content.proton.documentdb.ready.attribute.memory_usage.used_bytes", Unit.BYTE, "The number of used bytes (<= allocated_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_READY_ATTRIBUTE_MEMORY_USAGE_DEAD_BYTES("content.proton.documentdb.ready.attribute.memory_usage.dead_bytes", Unit.BYTE, "The number of dead bytes (<= used_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_READY_ATTRIBUTE_MEMORY_USAGE_ONHOLD_BYTES("content.proton.documentdb.ready.attribute.memory_usage.onhold_bytes", Unit.BYTE, "The number of bytes on hold"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_ATTRIBUTE_MEMORY_USAGE_ALLOCATED_BYTES("content.proton.documentdb.notready.attribute.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_ATTRIBUTE_MEMORY_USAGE_USED_BYTES("content.proton.documentdb.notready.attribute.memory_usage.used_bytes", Unit.BYTE, "The number of used bytes (<= allocated_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_ATTRIBUTE_MEMORY_USAGE_DEAD_BYTES("content.proton.documentdb.notready.attribute.memory_usage.dead_bytes", Unit.BYTE, "The number of dead bytes (<= used_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_NOTREADY_ATTRIBUTE_MEMORY_USAGE_ONHOLD_BYTES("content.proton.documentdb.notready.attribute.memory_usage.onhold_bytes", Unit.BYTE, "The number of bytes on hold"),

    // index
    CONTENT_PROTON_DOCUMENTDB_INDEX_MEMORY_USAGE_ALLOCATED_BYTES("content.proton.documentdb.index.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    CONTENT_PROTON_DOCUMENTDB_INDEX_MEMORY_USAGE_USED_BYTES("content.proton.documentdb.index.memory_usage.used_bytes", Unit.BYTE, "The number of used bytes (<= allocated_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_INDEX_MEMORY_USAGE_DEAD_BYTES("content.proton.documentdb.index.memory_usage.dead_bytes", Unit.BYTE, "The number of dead bytes (<= used_bytes)"),
    CONTENT_PROTON_DOCUMENTDB_INDEX_MEMORY_USAGE_ONHOLD_BYTES("content.proton.documentdb.index.memory_usage.onhold_bytes", Unit.BYTE, "The number of bytes on hold"),
    CONTENT_PROTON_DOCUMENTDB_INDEX_DISK_USAGE("content.proton.documentdb.index.disk_usage", Unit.BYTE, "Disk space usage in bytes"),

    // matching
    CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERIES("content.proton.documentdb.matching.queries", Unit.QUERY, "Number of queries executed"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_SOFT_DOOMED_QUERIES("content.proton.documentdb.matching.soft_doomed_queries", Unit.QUERY, "Number of queries hitting the soft timeout"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERY_LATENCY("content.proton.documentdb.matching.query_latency", Unit.SECOND, "Total average latency (sec) when matching and ranking a query"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_QUERY_SETUP_TIME("content.proton.documentdb.matching.query_setup_time", Unit.SECOND, "Average time (sec) spent setting up and tearing down queries"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_MATCHED("content.proton.documentdb.matching.docs_matched", Unit.DOCUMENT, "Number of documents matched"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_RANKED("content.proton.documentdb.matching.docs_ranked", Unit.DOCUMENT, "Number of documents ranked (first phase)"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_DOCS_RERANKED("content.proton.documentdb.matching.docs_reranked", Unit.DOCUMENT, "Number of documents re-ranked (second phase)"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERIES("content.proton.documentdb.matching.rank_profile.queries", Unit.QUERY, "Number of queries executed"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_SOFT_DOOMED_QUERIES("content.proton.documentdb.matching.rank_profile.soft_doomed_queries", Unit.QUERY, "Number of queries hitting the soft timeout"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_SOFT_DOOM_FACTOR("content.proton.documentdb.matching.rank_profile.soft_doom_factor", Unit.FRACTION, "Factor used to compute soft-timeout"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_LATENCY("content.proton.documentdb.matching.rank_profile.query_latency", Unit.SECOND, "Total average latency (sec) when matching and ranking a query"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_QUERY_SETUP_TIME("content.proton.documentdb.matching.rank_profile.query_setup_time", Unit.SECOND, "Average time (sec) spent setting up and tearing down queries"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_GROUPING_TIME("content.proton.documentdb.matching.rank_profile.grouping_time", Unit.SECOND, "Average time (sec) spent on grouping"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_RERANK_TIME("content.proton.documentdb.matching.rank_profile.rerank_time", Unit.SECOND, "Average time (sec) spent on 2nd phase ranking"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCS_MATCHED("content.proton.documentdb.matching.rank_profile.docs_matched", Unit.DOCUMENT, "Number of documents matched"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCS_RANKED("content.proton.documentdb.matching.rank_profile.docs_ranked", Unit.DOCUMENT, "Number of documents ranked (first phase)"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCS_RERANKED("content.proton.documentdb.matching.rank_profile.docs_reranked", Unit.DOCUMENT, "Number of documents re-ranked (second phase)"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_LIMITED_QUERIES("content.proton.documentdb.matching.rank_profile.limited_queries", Unit.QUERY, "Number of queries limited in match phase"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCID_PARTITION_ACTIVE_TIME("content.proton.documentdb.matching.rank_profile.docid_partition.active_time", Unit.SECOND, "Time (sec) spent doing actual work"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCID_PARTITION_DOCS_MATCHED("content.proton.documentdb.matching.rank_profile.docid_partition.docs_matched", Unit.DOCUMENT, "Number of documents matched"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCID_PARTITION_DOCS_RANKED("content.proton.documentdb.matching.rank_profile.docid_partition.docs_ranked", Unit.DOCUMENT, "Number of documents ranked (first phase)"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCID_PARTITION_DOCS_RERANKED("content.proton.documentdb.matching.rank_profile.docid_partition.docs_reranked", Unit.DOCUMENT, "Number of documents re-ranked (second phase)"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_DOCID_PARTITION_WAIT_TIME("content.proton.documentdb.matching.rank_profile.docid_partition.wait_time", Unit.SECOND, "Time (sec) spent waiting for other external threads and resources"),
    CONTENT_PROTON_DOCUMENTDB_MATCHING_RANK_PROFILE_MATCH_TIME("content.proton.documentdb.matching.rank_profile.match_time", Unit.SECOND, "Average time (sec) for matching a query (1st phase)"),

    // feeding
    CONTENT_PROTON_DOCUMENTDB_FEEDING_COMMIT_OPERATIONS("content.proton.documentdb.feeding.commit.operations", Unit.OPERATION, "Number of operations included in a commit"),
    CONTENT_PROTON_DOCUMENTDB_FEEDING_COMMIT_LATENCY("content.proton.documentdb.feeding.commit.latency", Unit.SECOND, "Latency for commit in seconds"),


    // Metrics emitters not used in any metrics sets
    CONTENT_PROTON_SESSION_CACHE_GROUPING_NUM_CACHED("content.proton.session_cache.grouping.num_cached", Unit.SESSION, "Number of currently cached sessions"),
    CONTENT_PROTON_SESSION_CACHE_GROUPING_NUM_DROPPED("content.proton.session_cache.grouping.num_dropped", Unit.SESSION, "Number of dropped cached sessions"),
    CONTENT_PROTON_SESSION_CACHE_GROUPING_NUM_INSERT("content.proton.session_cache.grouping.num_insert", Unit.SESSION, "Number of inserted sessions"),
    CONTENT_PROTON_SESSION_CACHE_GROUPING_NUM_PICK("content.proton.session_cache.grouping.num_pick", Unit.SESSION, "Number if picked sessions"),
    CONTENT_PROTON_SESSION_CACHE_GROUPING_NUM_TIMEDOUT("content.proton.session_cache.grouping.num_timedout", Unit.SESSION, "Number of timed out sessions"),
    CONTENT_PROTON_SESSION_CACHE_SEARCH_NUM_CACHED("content.proton.session_cache.search.num_cached", Unit.SESSION, "Number of currently cached sessions"),
    CONTENT_PROTON_SESSION_CACHE_SEARCH_NUM_DROPPED("content.proton.session_cache.search.num_dropped", Unit.SESSION, "Number of dropped cached sessions"),
    CONTENT_PROTON_SESSION_CACHE_SEARCH_NUM_INSERT("content.proton.session_cache.search.num_insert", Unit.SESSION, "Number of inserted sessions"),
    CONTENT_PROTON_SESSION_CACHE_SEARCH_NUM_PICK("content.proton.session_cache.search.num_pick", Unit.SESSION, "Number if picked sessions"),
    CONTENT_PROTON_SESSION_CACHE_SEARCH_NUM_TIMEDOUT("content.proton.session_cache.search.num_timedout", Unit.SESSION, "Number of timed out sessions"),

    METRICMANAGER_PERIODICHOOKLATENCY("metricmanager.periodichooklatency", Unit.MILLISECOND, "Time in ms used to update a single periodic hook"),
    METRICMANAGER_RESETLATENCY("metricmanager.resetlatency", Unit.MILLISECOND, "Time in ms used to reset all metrics."),
    METRICMANAGER_SLEEPTIME("metricmanager.sleeptime", Unit.MILLISECOND, "Time in ms worker thread is sleeping"),
    METRICMANAGER_SNAPSHOTHOOKLATENCY("metricmanager.snapshothooklatency", Unit.MILLISECOND, "Time in ms used to update a single snapshot hook"),
    METRICMANAGER_SNAPSHOTLATENCY("metricmanager.snapshotlatency", Unit.MILLISECOND, "Time in ms used to take a snapshot");


    private final String name;
    private final Unit unit;
    private final String description;

    SearchNodeMetrics(String name, Unit unit, String description) {
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
