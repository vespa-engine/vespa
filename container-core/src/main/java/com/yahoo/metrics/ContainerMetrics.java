package com.yahoo.metrics;

/**
 * @author gjoranv
 */
public enum ContainerMetrics {

    HTTP_STATUS_1XX("http.status.1xx", Unit.RESPONSE, "Number of responses with a 1xx status"),
    HTTP_STATUS_2XX("http.status.2xx", Unit.RESPONSE, "Number of responses with a 2xx status"),
    HTTP_STATUS_3XX("http.status.3xx", Unit.RESPONSE, "Number of responses with a 3xx status"),
    HTTP_STATUS_4XX("http.status.4xx", Unit.RESPONSE, "Number of responses with a 4xx status"),
    HTTP_STATUS_5XX("http.status.5xx", Unit.RESPONSE, "Number of responses with a 5xx status"),

    JDISC_GC_COUNT("jdisc.gc.count", Unit.OPERATION, "Number of JVM garbage collections done"),
    JDISC_GC_MS("jdisc.gc.ms", Unit.MILLISECOND, "Time spent in JVM garbage collection"),

    JDISC_HTTP_REQUESTS_STATUS("jdisc.http.requests.status", Unit.REQUEST, "Number of requests to the built-in status handler"),

    JDISC_THREAD_POOL_UNHANDLED_EXCEPTIONS("jdisc.thread_pool.unhandled_exceptions", Unit.THREAD, "Number of exceptions thrown by tasks"),
    JDISC_THREAD_POOL_WORK_QUEUE_CAPACITY("jdisc.thread_pool.work_queue.capacity", Unit.THREAD, "Capacity of the task queue"),
    JDISC_THREAD_POOL_WORK_QUEUE_SIZE("jdisc.thread_pool.work_queue.size", Unit.THREAD, "Size of the task queue"),
    JDISC_THREAD_POOL_REJECTED_TASKS("jdisc.thread_pool.rejected_tasks", Unit.THREAD, "Number of tasks rejected by the thread pool"),
    JDISC_THREAD_POOL_SIZE("jdisc.thread_pool.size", Unit.THREAD, "Size of the thread pool"),
    JDISC_THREAD_POOL_MAX_ALLOWED_SIZE("jdisc.thread_pool.max_allowed_size", Unit.THREAD, "The maximum allowed number of threads in the pool"),
    JDISC_THREAD_POOL_ACTIVE_THREADS("jdisc.thread_pool.active_threads", Unit.THREAD, "Number of threads that are active"),

    JETTY_THREADPOOL_MAX_THREADS("jdisc.http.jetty.threadpool.thread.max", Unit.THREAD, "Configured maximum number of threads"),
    JETTY_THREADPOOL_MIN_THREADS("jdisc.http.jetty.threadpool.thread.min", Unit.THREAD, "Configured minimum number of threads"),
    JETTY_THREADPOOL_RESERVED_THREADS("jdisc.http.jetty.threadpool.thread.reserved", Unit.THREAD, "Configured number of reserved threads or -1 for heuristic"),
    JETTY_THREADPOOL_BUSY_THREADS("jdisc.http.jetty.threadpool.thread.busy", Unit.THREAD, "Number of threads executing internal and transient jobs"),
    JETTY_THREADPOOL_IDLE_THREADS("jdisc.http.jetty.threadpool.thread.idle", Unit.THREAD, "Number of idle threads"),
    JETTY_THREADPOOL_TOTAL_THREADS("jdisc.http.jetty.threadpool.thread.total", Unit.THREAD, "Current number of threads"),
    JETTY_THREADPOOL_QUEUE_SIZE("jdisc.http.jetty.threadpool.queue.size", Unit.THREAD, "Current size of the job queue"),

    SERVER_NUM_OPEN_CONNECTIONS("serverNumOpenConnections", Unit.CONNECTION, "The number of currently open connections"),
    SERVER_NUM_CONNECTIONS("serverNumConnections", Unit.CONNECTION, "The total number of connections opened"),

    SERVER_BYTES_RECEIVED("serverBytesReceived", Unit.BYTE, "The number of bytes received by the server"),
    SERVER_BYTES_SENT("serverBytesSent", Unit.BYTE, "The number of bytes sent from the server"),

    HANDLED_REQUESTS("handled.requests", Unit.OPERATION, "The number of requests handled per metrics snapshot"),
    HANDLED_LATENCY("handled.latency", Unit.MILLISECOND, "The time used for requests during this metrics snapshot"),
    
    HTTPAPI_LATENCY("httpapi_latency", Unit.MILLISECOND, "Duration for requests to the HTTP document APIs"),
    HTTPAPI_PENDING("httpapi_pending", Unit.OPERATION, "Document operations pending execution"),
    HTTPAPI_NUM_OPERATIONS("httpapi_num_operations", Unit.OPERATION, "Total number of document operations performed"),
    HTTPAPI_NUM_UPDATES("httpapi_num_updates", Unit.OPERATION, "Document update operations performed"),
    HTTPAPI_NUM_REMOVES("httpapi_num_removes", Unit.OPERATION, "Document remove operations performed"),
    HTTPAPI_NUM_PUTS("httpapi_num_puts", Unit.OPERATION, "Document put operations performed"),
    HTTPAPI_SUCCEEDED("httpapi_succeeded", Unit.OPERATION, "Document operations that succeeded"),
    HTTPAPI_FAILED("httpapi_failed", Unit.OPERATION, "Document operations that failed"),
    HTTPAPI_PARSE_ERROR("httpapi_parse_error", Unit.OPERATION, "Document operations that failed due to document parse errors"),
    HTTPAPI_CONDITION_NOT_MET("httpapi_condition_not_met", Unit.OPERATION, "Document operations not applied due to condition not met"),
    HTTPAPI_NOT_FOUND("httpapi_not_found", Unit.OPERATION, "Document operations not applied due to document not found"),
    
    MEM_HEAP_TOTAL("mem.heap.total", Unit.BYTE, "Total available heap memory"),
    MEM_HEAP_FREE("mem.heap.free", Unit.BYTE, "Free heap memory"),
    MEM_HEAP_USED("mem.heap.used", Unit.BYTE, "Currently used heap memory"),
    MEM_DIRECT_TOTAL("mem.direct.total", Unit.BYTE, "Total available direct memory"),
    MEM_DIRECT_FREE("mem.direct.free", Unit.BYTE, "Currently free direct memory"),
    MEM_DIRECT_USED("mem.direct.used", Unit.BYTE, "Direct memory currently used"),
    MEM_DIRECT_COUNT("mem.direct.count", Unit.BYTE, "Number of direct memory allocations"),
    MEM_NATIVE_TOTAL("mem.native.total", Unit.BYTE, "Total available native memory"),
    MEM_NATIVE_FREE("mem.native.free", Unit.BYTE, "Currently free native memory"),
    MEM_NATIVE_USED("mem.native.used", Unit.BYTE, "Native memory currently used"),    
    
    
    // SearchChain metrics
    PEAK_QPS("peak_qps", Unit.OPERATION_PER_SECOND, "The highest number of qps for a second for this metrics shapshot"),
    SEARCH_CONNECTIONS("search_connections", Unit.CONNECTION, "Number of search connections"),
    FEED_LATENCY("feed.latency", Unit.MILLISECOND, "Feed latency"),
    FEED_HTTP_REQUESTS("feed.http-requests", Unit.OPERATION, "Feed HTTP requests"),
    QUERIES("queries", Unit.OPERATION, "Query volume"),
    QUERY_CONTAINER_LATENCY("query_container_latency", Unit.MILLISECOND, "The query execution time consumed in the container"),
    QUERY_LATENCY("query_latency", Unit.MILLISECOND, "The overall query latency as seen by the container"),
    QUERY_TIMEOUT("query_timeout", Unit.MILLISECOND, "The amount of time allowed for query execytion, from the client"),
    FAILED_QUERIES("failed_queries", Unit.OPERATION, "The number of failed queries"),
    DEGRADED_QUERIES("degraded_queries", Unit.OPERATION, "The number of degraded queries, e.g. due to some conent nodes not responding in time"),
    HITS_PER_QUERY("hits_per_query", Unit.HIT, "The number of hits returned"),
    QUERY_HIT_OFFSET("query_hit_offset", Unit.HIT, "The offset for hits returned"),
    DOCUMENTS_COVERED("documents_covered", Unit.DOCUMENT, "The combined number of documents considered during query evaluation"),
    DOCUMENTS_TOTAL("documents_total", Unit.DOCUMENT, "The number of documents to be evaluated if all requests had been fully executed"),
    DOCUMENTS_TARGET_TOTAL("documents_target_total", Unit.DOCUMENT, "The target number of total documents to be evaluated when when all data is in sync"),
    JDISC_RENDER_LATENCY("jdisc.render.latency", Unit.MILLISECOND, "The time used by the container to render responses"),
    QUERY_ITEM_COUNT("query_item_count", Unit.ITEM, "The number of query items (terms, phrases, etc)"),
    
    TOTAL_HITS_PER_QUERY("totalhits_per_query", Unit.HIT, "The total number of documents found to match queries"),
    EMPTY_RESULTS("empty_results", Unit.OPERATION, "Number of queries matching no documents"),
    REQUESTS_OVER_QUOTA("requestsOverQuota", Unit.OPERATION, "The number of requests rejected due to exceeding quota"),
    
    RELEVANCE_AT_1("relevance.at_1", Unit.SCORE, "The relevance of hit number 1"),
    RELEVANCE_AT_3("relevance.at_3", Unit.SCORE, "The relevance of hit number 3"),
    RELEVANCE_AT_10("relevance.at_10", Unit.SCORE, "The relevance of hit number 10"),

    // Errors from search container
    ERROR_TIMEOUT("error.timeout", Unit.OPERATION, "Requests that timed out"),
    ERROR_BACKENDS_OOS("error.backends_oos", Unit.OPERATION, "Requests that failed due to no available backends nodes"),
    ERROR_PLUGIN_FAILURE("error.plugin_failure", Unit.OPERATION, "Requests that failed due to plugin failure"),
    ERROR_BACKEND_COMMUNICATION_ERROR("error.backend_communication_error", Unit.OPERATION, "Requests that failed due to backend communication error"),
    ERROR_EMPTY_DOCUMENT_SUMMARIES("error.empty_document_summaries", Unit.OPERATION, "Requests that failed due to missing document summaries"),
    ERROR_INVALID_QUERY_PARAMETER("error.invalid_query_parameter", Unit.OPERATION, "Requests that failed due to invalid query parameters"),
    ERROR_INTERNAL_SERVER_ERROR("error.internal_server_error", Unit.OPERATION, "Requests that failed due to internal server error"),
    ERROR_MISCONFIGURED_SERVER("error.misconfigured_server", Unit.OPERATION, "Requests that failed due to misconfigured server"),
    ERROR_INVALID_QUERY_TRANSFORMATION("error.invalid_query_transformation", Unit.OPERATION, "Requests that failed due to invalid query transformation"),
    ERROR_RESULTS_WITH_ERRORS("error.results_with_errors", Unit.OPERATION, "The number of queries with error payload"),
    ERROR_UNSPECIFIED("error.unspecified", Unit.OPERATION, "Requests that failed for an unspecified reason"),
    ERROR_UNHANDLED_EXCEPTION("error.unhandled_exception", Unit.OPERATION, "Requests that failed due to an unhandled exception");
    
    
    private final String name;
    private final Unit unit;
    private final String description;

    ContainerMetrics(String name, Unit unit, String description) {
        this.name = name;
        this.unit = unit;
        this.description = description;
    }

    public String baseName() {
        return name;
    }

    public String description() {
        return description + " (unit: " + unit.shortName() + ")";
    }

    private String withSuffix(Suffix suffix) {
        return baseName() + "." + suffix.suffix();
    }

    public String ninety_five_percentile() {
        return withSuffix(Suffix.ninety_five_percentile);
    }

    public String ninety_nine_percentile() {
        return withSuffix(Suffix.ninety_nine_percentile);
    }

    public String average() {
        return withSuffix(Suffix.average);
    }

    public String count() {
        return withSuffix(Suffix.count);
    }

    public String last() {
        return withSuffix(Suffix.last);
    }

    public String max() {
        return withSuffix(Suffix.max);
    }

    public String min() {
        return withSuffix(Suffix.min);
    }

    public String rate() {
        return withSuffix(Suffix.rate);
    }

    public String sum() {
        return withSuffix(Suffix.sum);
    }

}
