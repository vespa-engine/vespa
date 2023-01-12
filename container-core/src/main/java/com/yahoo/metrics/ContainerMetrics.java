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
    HTTPAPI_NOT_FOUND("httpapi_not_found", Unit.OPERATION, "Document operations not applied due to document not found");
    
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
