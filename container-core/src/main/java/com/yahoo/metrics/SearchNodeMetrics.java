package com.yahoo.metrics;

/**
 * @author gjoranv
 */
public enum SearchNodeMetrics implements VespaMetrics {

    CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_TOTAL("content.proton.documentdb.documents.total", Unit.DOCUMENT, "The total number of documents in this documents db (ready + not-ready)"),
    CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_READY("content.proton.documentdb.documents.ready", Unit.DOCUMENT, "The number of ready documents in this document db"),
    CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_ACTIVE("content.proton.documentdb.documents.active", Unit.DOCUMENT, "The number of active / searchable documents in this document db"),
    CONTENT_PROTON_DOCUMENTDB_DOCUMENTS_REMOVED("content.proton.documentdb.documents.removed", Unit.DOCUMENT, "The number of removed documents in this document db"),

    CONTENT_PROTON_DOCUMENTDB_INDEX_DOCS_IN_MEMORY("content.proton.documentdb.index.docs_in_memory", Unit.DOCUMENT, "Number of documents in memory index"),
    CONTENT_PROTON_DOCUMENTDB_DISK_USAGE("content.proton.documentdb.disk_usage", Unit.BYTE, "The total disk usage (in bytes) for this document db"),
    CONTENT_PROTON_DOCUMENTDB_MEMORY_USAGE_ALLOCATED_BYTES("content.proton.documentdb.memory_usage.allocated_bytes", Unit.BYTE, "The number of allocated bytes"),
    CONTENT_PROTON_DOCUMENTDB_HEART_BEAT_AGE("content.proton.documentdb.heart_beat_age", Unit.SECOND, "How long ago (in seconds) heart beat maintenace job was run"),
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

    // jobs
    CONTENT_PROTON_DOCUMENTDB_JOB_TOTAL("content.proton.documentdb.job.total", Unit.FRACTION, "The job load average total of all job metrics"),
    CONTENT_PROTON_DOCUMENTDB_JOB_ATTRIBUTE_FLUSH("content.proton.documentdb.job.attribute_flush", Unit.FRACTION, "Flushing of attribute vector(s) to disk"),
    CONTENT_PROTON_DOCUMENTDB_JOB_MEMORY_INDEX_FLUSH("content.proton.documentdb.job.memory_index_flush", Unit.FRACTION, "Flushing of memory index to disk"),
    CONTENT_PROTON_DOCUMENTDB_JOB_DISK_INDEX_FUSION("content.proton.documentdb.job.disk_index_fusion", Unit.FRACTION, "Fusion of disk indexes"),
    CONTENT_PROTON_DOCUMENTDB_JOB_DOCUMENT_STORE_FLUSH("content.proton.documentdb.job.document_store_flush", Unit.FRACTION, "Flushing of document store to disk"),
    CONTENT_PROTON_DOCUMENTDB_JOB_DOCUMENT_STORE_COMPACT("content.proton.documentdb.job.document_store_compact", Unit.FRACTION, "Compaction of document store on disk"),
    CONTENT_PROTON_DOCUMENTDB_JOB_BUCKET_MOVE("content.proton.documentdb.job.bucket_move", Unit.FRACTION, "Moving of buckets between 'ready' and 'notready' sub databases"),
    CONTENT_PROTON_DOCUMENTDB_JOB_LID_SPACE_COMPACT("content.proton.documentdb.job.lid_space_compact", Unit.FRACTION, "Compaction of lid space in document meta store and attribute vectors"),
    CONTENT_PROTON_DOCUMENTDB_JOB_REMOVED_DOCUMENTS_PRUNE("content.proton.documentdb.job.removed_documents_prune", Unit.FRACTION, "Pruning of removed documents in 'removed' sub database");




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
