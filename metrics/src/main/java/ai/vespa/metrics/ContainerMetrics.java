// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics;

/**
 * @author gjoranv
 */
public enum ContainerMetrics implements VespaMetrics {

    HTTP_STATUS_1XX("http.status.1xx", Unit.RESPONSE, "Number of responses with a 1xx status"),
    HTTP_STATUS_2XX("http.status.2xx", Unit.RESPONSE, "Number of responses with a 2xx status"),
    HTTP_STATUS_3XX("http.status.3xx", Unit.RESPONSE, "Number of responses with a 3xx status"),
    HTTP_STATUS_4XX("http.status.4xx", Unit.RESPONSE, "Number of responses with a 4xx status"),
    HTTP_STATUS_5XX("http.status.5xx", Unit.RESPONSE, "Number of responses with a 5xx status"),

    APPLICATION_GENERATION("application_generation", Unit.VERSION, "The currently live application config generation (aka session id)"),
    IN_SERVICE("in_service", Unit.BINARY, "This will have the value 1 if the node is in service, 0 if not."),

    JDISC_GC_COUNT("jdisc.gc.count", Unit.OPERATION, "Number of JVM garbage collections done"),
    JDISC_GC_MS("jdisc.gc.ms", Unit.MILLISECOND, "Time spent in JVM garbage collection"),
    JDISC_JVM("jdisc.jvm", Unit.VERSION, "JVM runtime version"),
    CPU("cpu", Unit.THREAD, "Container service CPU pressure"),
    JDISC_MEMORY_MAPPINGS("jdisc.memory_mappings", Unit.OPERATION, "JDISC Memory mappings"),
    JDISC_OPEN_FILE_DESCRIPTORS("jdisc.open_file_descriptors", Unit.ITEM, "JDISC Open file descriptors"),

    JDISC_THREAD_POOL_UNHANDLED_EXCEPTIONS("jdisc.thread_pool.unhandled_exceptions", Unit.THREAD, "Number of exceptions thrown by tasks"),
    JDISC_THREAD_POOL_WORK_QUEUE_CAPACITY("jdisc.thread_pool.work_queue.capacity", Unit.THREAD, "Capacity of the task queue"),
    JDISC_THREAD_POOL_WORK_QUEUE_SIZE("jdisc.thread_pool.work_queue.size", Unit.THREAD, "Size of the task queue"),
    JDISC_THREAD_POOL_REJECTED_TASKS("jdisc.thread_pool.rejected_tasks", Unit.THREAD, "Number of tasks rejected by the thread pool"),
    JDISC_THREAD_POOL_SIZE("jdisc.thread_pool.size", Unit.THREAD, "Size of the thread pool"),
    JDISC_THREAD_POOL_MAX_ALLOWED_SIZE("jdisc.thread_pool.max_allowed_size", Unit.THREAD, "The maximum allowed number of threads in the pool"),
    JDISC_THREAD_POOL_ACTIVE_THREADS("jdisc.thread_pool.active_threads", Unit.THREAD, "Number of threads that are active"),
    
    JDISC_DEACTIVATED_CONTAINERS_TOTAL("jdisc.deactivated_containers.total", Unit.ITEM, "JDISC Deactivated container instances"),
    JDISC_DEACTIVATED_CONTAINERS_WITH_RETAINED_REFS("jdisc.deactivated_containers.with_retained_refs.last", Unit.ITEM, "JDISC Deactivated container nodes with retained refs"),
    JDISC_APPLICATION_FAILED_COMPONENT_GRAPHS("jdisc.application.failed_component_graphs", Unit.ITEM, "JDISC Application failed component graphs"),
    JDISC_APPLICATION_COMPONENT_GRAPH_CREATION_TIME_MILLIS("jdisc.application.component_graph.creation_time_millis", Unit.MILLISECOND, "JDISC Application component graph creation time"),
    JDISC_APPLICATION_COMPONENT_GRAPH_RECONFIGURATIONS("jdisc.application.component_graph.reconfigurations", Unit.ITEM, "JDISC Application component graph reconfigurations"),

    JDISC_SINGLETON_IS_ACTIVE("jdisc.singleton.is_active", Unit.ITEM, "JDISC Singleton is active"),
    JDISC_SINGLETON_ACTIVATION_COUNT("jdisc.singleton.activation.count", Unit.OPERATION, "JDISC Singleton activations"),
    JDISC_SINGLETON_ACTIVATION_FAILURE_COUNT("jdisc.singleton.activation.failure.count", Unit.OPERATION, "JDISC Singleton activation failures"),
    JDISC_SINGLETON_ACTIVATION_MILLIS("jdisc.singleton.activation.millis", Unit.MILLISECOND, "JDISC Singleton activation time"),
    JDISC_SINGLETON_DEACTIVATION_COUNT("jdisc.singleton.deactivation.count", Unit.OPERATION, "JDISC Singleton deactivations"),
    JDISC_SINGLETON_DEACTIVATION_FAILURE_COUNT("jdisc.singleton.deactivation.failure.count", Unit.OPERATION, "JDISC Singleton deactivation failures"),
    JDISC_SINGLETON_DEACTIVATION_MILLIS("jdisc.singleton.deactivation.millis", Unit.MILLISECOND, "JDISC Singleton deactivation time"),

    JDISC_HTTP_SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT("jdisc.http.ssl.handshake.failure.missing_client_cert", Unit.OPERATION, "JDISC HTTP SSL Handshake failures due to missing client certificate"),
    JDISC_HTTP_SSL_HANDSHAKE_FAILURE_EXPIRED_CLIENT_CERT("jdisc.http.ssl.handshake.failure.expired_client_cert", Unit.OPERATION, "JDISC HTTP SSL Handshake failures due to expired client certificate"),
    JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INVALID_CLIENT_CERT("jdisc.http.ssl.handshake.failure.invalid_client_cert", Unit.OPERATION, "JDISC HTTP SSL Handshake failures due to invalid client certificate"),
    JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS("jdisc.http.ssl.handshake.failure.incompatible_protocols", Unit.OPERATION, "JDISC HTTP SSL Handshake failures due to incompatible protocols"),
    JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CHIFERS("jdisc.http.ssl.handshake.failure.incompatible_chifers", Unit.OPERATION, "JDISC HTTP SSL Handshake failures due to incompatible chifers"),
    JDISC_HTTP_SSL_HANDSHAKE_FAILURE_CONNECTION_CLOSED("jdisc.http.ssl.handshake.failure.connection_closed", Unit.OPERATION, "JDISC HTTP SSL Handshake failures due to connection closed"),
    JDISC_HTTP_SSL_HANDSHAKE_FAILURE_UNKNOWN("jdisc.http.ssl.handshake.failure.unknown", Unit.OPERATION, "JDISC HTTP SSL Handshake failures for unknown reason"),

    JDISC_HTTP_REQUEST_PREMATURELY_CLOSED("jdisc.http.request.prematurely_closed", Unit.REQUEST, "HTTP requests prematurely closed"),
    JDISC_HTTP_REQUEST_REQUESTS_PER_CONNECTION("jdisc.http.request.requests_per_connection", Unit.REQUEST, "HTTP requests per connection"),
    JDISC_HTTP_REQUEST_URI_LENGTH("jdisc.http.request.uri_length", Unit.BYTE, "HTTP URI length"),
    JDISC_HTTP_REQUEST_CONTENT_SIZE("jdisc.http.request.content_size", Unit.BYTE, "HTTP request content size"),
    JDISC_HTTP_REQUESTS("jdisc.http.requests", Unit.REQUEST, "HTTP requests"),
    JDISC_HTTP_REQUESTS_STATUS("jdisc.http.requests.status", Unit.REQUEST, "Number of requests to the built-in status handler"),

    JDISC_HTTP_FILTER_RULE_BLOCKED_REQUESTS("jdisc.http.filter.rule.blocked_requests", Unit.REQUEST, "Number of requests blocked by filter"),
    JDISC_HTTP_FILTER_RULE_ALLOWED_REQUESTS("jdisc.http.filter.rule.allowed_requests", Unit.REQUEST, "Number of requests allowed by filter"),
    JDISC_HTTP_FILTERING_REQUEST_HANDLED("jdisc.http.filtering.request.handled", Unit.REQUEST, "Number of filtering requests handled"),
    JDISC_HTTP_FILTERING_REQUEST_UNHANDLED("jdisc.http.filtering.request.unhandled", Unit.REQUEST, "Number of filtering requests unhandled"),
    JDISC_HTTP_FILTERING_RESPONSE_HANDLED("jdisc.http.filtering.response.handled", Unit.REQUEST, "Number of filtering responses handled"),
    JDISC_HTTP_FILTERING_RESPONSE_UNHANDLED("jdisc.http.filtering.response.unhandled", Unit.REQUEST, "Number of filtering responses unhandled"),
    JDISC_HTTP_HANDLER_UNHANDLED_EXCEPTIONS("jdisc.http.handler.unhandled_exceptions", Unit.REQUEST, "Number of unhandled exceptions in handler"),

    JDISC_TLS_CAPABILITY_CHECKS_SUCCEEDED("jdisc.tls.capability_checks.succeeded", Unit.OPERATION, "Number of TLS capability checks succeeded"),
    JDISC_TLS_CAPABILITY_CHECKS_FAILED("jdisc.tls.capability_checks.failed", Unit.OPERATION, "Number of TLS capability checks failed"),

    JETTY_THREADPOOL_MAX_THREADS("jdisc.http.jetty.threadpool.thread.max", Unit.THREAD, "Configured maximum number of threads"),
    JETTY_THREADPOOL_MIN_THREADS("jdisc.http.jetty.threadpool.thread.min", Unit.THREAD, "Configured minimum number of threads"),
    JETTY_THREADPOOL_RESERVED_THREADS("jdisc.http.jetty.threadpool.thread.reserved", Unit.THREAD, "Configured number of reserved threads or -1 for heuristic"),
    JETTY_THREADPOOL_BUSY_THREADS("jdisc.http.jetty.threadpool.thread.busy", Unit.THREAD, "Number of threads executing internal and transient jobs"),
    JETTY_THREADPOOL_IDLE_THREADS("jdisc.http.jetty.threadpool.thread.idle", Unit.THREAD, "Number of idle threads"),
    JETTY_THREADPOOL_TOTAL_THREADS("jdisc.http.jetty.threadpool.thread.total", Unit.THREAD, "Current number of threads"),
    JETTY_THREADPOOL_QUEUE_SIZE("jdisc.http.jetty.threadpool.queue.size", Unit.THREAD, "Current size of the job queue"),
    JETTY_HTTP_COMPLIANCE_VIOLATION("jdisc.http.jetty.http_compliance.violation", Unit.FAILURE, "Number of HTTP compliance violations"),

    SERVER_NUM_OPEN_CONNECTIONS("serverNumOpenConnections", Unit.CONNECTION, "The number of currently open connections"),
    SERVER_NUM_CONNECTIONS("serverNumConnections", Unit.CONNECTION, "The total number of connections opened"),

    SERVER_BYTES_RECEIVED("serverBytesReceived", Unit.BYTE, "The number of bytes received by the server"),
    SERVER_BYTES_SENT("serverBytesSent", Unit.BYTE, "The number of bytes sent from the server"),

    HANDLED_REQUESTS("handled.requests", Unit.OPERATION, "The number of requests handled per metrics snapshot"),
    HANDLED_LATENCY("handled.latency", Unit.MILLISECOND, "The time used for handling requests, excluding HTTP layer and rendering"),
    
    HTTPAPI_LATENCY("httpapi_latency", Unit.MILLISECOND, "Duration for requests to the HTTP document APIs"),
    HTTPAPI_PENDING("httpapi_pending", Unit.OPERATION, "Document operations pending execution"),
    HTTPAPI_NUM_OPERATIONS("httpapi_num_operations", Unit.OPERATION, "Total number of document operations performed"),
    HTTPAPI_NUM_UPDATES("httpapi_num_updates", Unit.OPERATION, "Document update operations performed"),
    HTTPAPI_NUM_REMOVES("httpapi_num_removes", Unit.OPERATION, "Document remove operations performed"),
    HTTPAPI_NUM_PUTS("httpapi_num_puts", Unit.OPERATION, "Document put operations performed"),
    HTTPAPI_OPS_PER_SEC("httpapi_ops_per_sec", Unit.OPERATION_PER_SECOND, "Document operations per second"), // TODO: Remove in Vespa 9
    HTTPAPI_SUCCEEDED("httpapi_succeeded", Unit.OPERATION, "Document operations that succeeded"),
    HTTPAPI_FAILED("httpapi_failed", Unit.OPERATION, "Document operations that failed"),
    HTTPAPI_PARSE_ERROR("httpapi_parse_error", Unit.OPERATION, "Document operations that failed due to document parse errors"),
    HTTPAPI_CONDITION_NOT_MET("httpapi_condition_not_met", Unit.OPERATION, "Document operations not applied due to condition not met"),
    HTTPAPI_NOT_FOUND("httpapi_not_found", Unit.OPERATION, "Document operations not applied due to document not found"),
    HTTPAPI_FAILED_UNKNOWN("httpapi_failed_unknown", Unit.OPERATION, "Document operations failed by unknown cause"),
    HTTPAPI_FAILED_TIMEOUT("httpapi_failed_timeout", Unit.OPERATION, "Document operations failed by timeout"),
    HTTPAPI_FAILED_INSUFFICIENT_STORAGE("httpapi_failed_insufficient_storage", Unit.OPERATION, "Document operations failed by insufficient storage"),
    HTTPAPI_QUEUED_OPERATIONS("httpapi_queued_operations", Unit.OPERATION, "Document operations queued for execution in /document/v1 API handler"),
    HTTPAPI_QUEUED_BYTES("httpapi_queued_bytes", Unit.BYTE, "Total operation bytes queued for execution in /document/v1 API handler"),
    HTTPAPI_QUEUED_AGE("httpapi_queued_age", Unit.SECOND, "Age in seconds of the oldest operation in the queue for /document/v1 API handler"),
    HTTPAPI_MBUS_WINDOW_SIZE("httpapi_mbus_window_size", Unit.OPERATION, "The window size of Messagebus's dynamic throttle policy for /document/v1 API handler"),

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
    
    ATHENZ_TENANT_CERT_EXPIRY_SECONDS("athenz-tenant-cert.expiry.seconds", Unit.SECOND, "Time remaining until Athenz tenant certificate expires"),
    CONTAINER_IAM_ROLE_EXPIRY_SECONDS("container-iam-role.expiry.seconds", Unit.SECOND, "Time remaining until IAM role expires"),
    
    
    // SearchChain metrics
    PEAK_QPS("peak_qps", Unit.QUERY_PER_SECOND, "The highest number of qps for a second for this metrics snapshot"),
    SEARCH_CONNECTIONS("search_connections", Unit.CONNECTION, "Number of search connections"),
    FEED_OPERATIONS("feed.operations", Unit.OPERATION, "Number of document feed operations"),
    FEED_LATENCY("feed.latency", Unit.MILLISECOND, "Feed latency"),
    FEED_HTTP_REQUESTS("feed.http-requests", Unit.OPERATION, "Feed HTTP requests"),
    QUERIES("queries", Unit.OPERATION, "Query volume"),
    QUERY_CONTAINER_LATENCY("query_container_latency", Unit.MILLISECOND, "The query execution time consumed in the container"),
    QUERY_LATENCY("query_latency", Unit.MILLISECOND, "The overall query latency as observed by the container cluster, excluding HTTP layer and rendering"),
    QUERY_TIMEOUT("query_timeout", Unit.MILLISECOND, "The amount of time allowed for query execution, from the client"),
    FAILED_QUERIES("failed_queries", Unit.OPERATION, "The number of failed queries"),
    DEGRADED_QUERIES("degraded_queries", Unit.OPERATION, "The number of degraded queries, e.g. due to some content nodes not responding in time"),
    HITS_PER_QUERY("hits_per_query", Unit.HIT_PER_QUERY, "The number of hits returned"),
    QUERY_HIT_OFFSET("query_hit_offset", Unit.HIT, "The offset for hits returned"),
    DOCUMENTS_COVERED("documents_covered", Unit.DOCUMENT, "The combined number of documents considered during query evaluation"),
    DOCUMENTS_TOTAL("documents_total", Unit.DOCUMENT, "The number of documents to be evaluated if all requests had been fully executed"),
    DOCUMENTS_TARGET_TOTAL("documents_target_total", Unit.DOCUMENT, "The target number of total documents to be evaluated when all data is in sync"),
    JDISC_RENDER_LATENCY("jdisc.render.latency", Unit.NANOSECOND, "The time used by the container to render responses"),
    QUERY_ITEM_COUNT("query_item_count", Unit.ITEM, "The number of query items (terms, phrases, etc.)"),
    DOCPROC_PROC_TIME("docproc.proctime", Unit.MILLISECOND, "Time spent processing document"),
    DOCPROC_DOCUMENTS("docproc.documents", Unit.DOCUMENT, "Number of processed documents"),
    
    TOTAL_HITS_PER_QUERY("totalhits_per_query", Unit.HIT_PER_QUERY, "The total number of documents found to match queries"),
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
    ERROR_ILLEGAL_QUERY("error.illegal_query", Unit.OPERATION, "Requests that failed due to illegal queries"),
    ERROR_INVALID_QUERY_PARAMETER("error.invalid_query_parameter", Unit.OPERATION, "Requests that failed due to invalid query parameters"),
    ERROR_INTERNAL_SERVER_ERROR("error.internal_server_error", Unit.OPERATION, "Requests that failed due to internal server error"),
    ERROR_MISCONFIGURED_SERVER("error.misconfigured_server", Unit.OPERATION, "Requests that failed due to misconfigured server"),
    ERROR_INVALID_QUERY_TRANSFORMATION("error.invalid_query_transformation", Unit.OPERATION, "Requests that failed due to invalid query transformation"),
    ERROR_RESULTS_WITH_ERRORS("error.results_with_errors", Unit.OPERATION, "The number of queries with error payload"),
    ERROR_UNSPECIFIED("error.unspecified", Unit.OPERATION, "Requests that failed for an unspecified reason"),
    ERROR_UNHANDLED_EXCEPTION("error.unhandled_exception", Unit.OPERATION, "Requests that failed due to an unhandled exception"),

    // Deprecated metrics. TODO: Remove in Vespa 9.
    SERVER_REJECTED_REQUESTS("serverRejectedRequests", Unit.OPERATION, "Deprecated. Use jdisc.thread_pool.rejected_tasks instead."), // TODO: Remove in Vespa 9.
    SERVER_THREAD_POOL_SIZE("serverThreadPoolSize", Unit.THREAD, "Deprecated. Use jdisc.thread_pool.size instead."), // TODO: Remove in Vespa 9.
    SERVER_ACTIVE_THREADS("serverActiveThreads", Unit.THREAD, "Deprecated. Use jdisc.thread_pool.active_threads instead."), // TODO: Remove in Vespa 9.

    // Java (JRT) TLS metrics
    JRT_TRANSPORT_TLS_CERTIFICATE_VERIFICATION_FAILURES("jrt.transport.tls-certificate-verification-failures", Unit.FAILURE, "TLS certificate verification failures"),
    JRT_TRANSPORT_PEER_AUTHORIZATION_FAILURES("jrt.transport.peer-authorization-failures", Unit.FAILURE, "TLS peer authorization failures"),
    JRT_TRANSPORT_SERVER_TLS_CONNECTIONS_ESTABLISHED("jrt.transport.server.tls-connections-established", Unit.CONNECTION, "TLS server connections established"),
    JRT_TRANSPORT_CLIENT_TLS_CONNECTIONS_ESTABLISHED("jrt.transport.client.tls-connections-established", Unit.CONNECTION, "TLS client connections established"),
    JRT_TRANSPORT_SERVER_UNENCRYPTED_CONNECTIONS_ESTABLISHED("jrt.transport.server.unencrypted-connections-established", Unit.CONNECTION, "Unencrypted server connections established"),
    JRT_TRANSPORT_CLIENT_UNENCRYPTED_CONNECTIONS_ESTABLISHED("jrt.transport.client.unencrypted-connections-established", Unit.CONNECTION, "Unencrypted client connections established"),

    MAX_QUERY_LATENCY("max_query_latency", Unit.MILLISECOND, "Deprecated. Use query_latency.max instead"), // TODO: Remove in Vespa 9
    MEAN_QUERY_LATENCY("mean_query_latency", Unit.MILLISECOND, "Deprecated. Use the expression (query_latency.sum / query_latency.count) instead"),// TODO: Remove in Vespa 9


    // Metrics defined in com/yahoo/jdisc/http/filter/security/athenz/AthenzAuthorizationFilter.java
    JDISC_HTTP_FILTER_ATHENZ_ACCEPTED_REQUESTS("jdisc.http.filter.athenz.accepted_requests", Unit.REQUEST, "Number of requests accepted by the AthenzAuthorization filter"),
    JDISC_HTTP_FILTER_ATHENZ_REJECTED_REQUESTS("jdisc.http.filter.athenz.rejected_requests", Unit.REQUEST, "Number of requests rejected by the AthenzAuthorization filter"),
    // Temporary metric to track grid usage
    JDISC_HTTP_FILTER_ATHENZ_GRID_REQUESTS("jdisc.http.filter.athenz.grid_requests", Unit.REQUEST, "Number of grid requests"),


    // Metrics defined in com/yahoo/jdisc/http/server/jetty/MetricDefinitions.java
    SERVER_CONNECTIONS_OPEN_MAX("serverConnectionsOpenMax", Unit.CONNECTION, "Maximum number of open connections"),
    SERVER_CONNECTION_DURATION_MAX("serverConnectionDurationMax", Unit.MILLISECOND, "Longest duration a connection is kept open"),

    SERVER_CONNECTION_DURATION_MEAN("serverConnectionDurationMean", Unit.MILLISECOND, "Average duration a connection is kept open"),
    SERVER_CONNECTION_DURATION_STD_DEV("serverConnectionDurationStdDev", Unit.MILLISECOND, "Standard deviation of open connection duration"),
    SERVER_NUM_REQUESTS("serverNumRequests", Unit.REQUEST, "Number of requests"),
    SERVER_NUM_SUCCESSFUL_RESPONSES("serverNumSuccessfulResponses", Unit.REQUEST, "Number of successful responses"),
    SERVER_NUM_FAILED_RESPONSES("serverNumFailedResponses", Unit.REQUEST, "Number of failed responses"),

    SERVER_NUM_SUCCESSFUL_RESPONSE_WRITES("serverNumSuccessfulResponseWrites", Unit.REQUEST, "Number of successful response writes"),
    SERVER_NUM_FAILED_RESPONSE_WRITES("serverNumFailedResponseWrites", Unit.REQUEST, "Number of failed response writes"),

    SERVER_TOTAL_SUCCESSFUL_RESPONSE_LATENCY("serverTotalSuccessfulResponseLatency", Unit.MILLISECOND, "Total duration for execution of successful responses"),
    SERVER_TOTAL_FAILED_RESPONSE_LATENCY("serverTotalFailedResponseLatency", Unit.MILLISECOND, "Total duration for execution of failed responses"),
    SERVER_TIME_TO_FIRST_BYTE("serverTimeToFirstByte", Unit.MILLISECOND, "Time from request has been received by the server until the first byte is returned to the client"),

    SERVER_STARTED_MILLIS("serverStartedMillis", Unit.MILLISECOND, "Time since the service was started"),

    EMBEDDER_LATENCY("embedder.latency", Unit.MILLISECOND, "Time spent creating an embedding"),
    EMBEDDER_SEQUENCE_LENGTH("embedder.sequence_length", Unit.ITEM, "Number of tokens in the input sequence"),
    EMBEDDER_REQUEST_COUNT("embedder.request.count", Unit.REQUEST, "Number of embedder API requests"),
    EMBEDDER_REQUEST_FAILURE_COUNT("embedder.request.failure.count", Unit.REQUEST, "Number of failed embedder API requests"),

    EMBEDDER_BATCH_SIZE("embedder.batch.size", Unit.ITEM, "Number of items in each dispatched batch"),
    EMBEDDER_BATCH_QUEUE_TIME("embedder.batch.queue_time", Unit.MILLISECOND, "Time spent waiting in queue before batch dispatch"),
    EMBEDDER_BATCH_COUNT("embedder.batch.count", Unit.OPERATION, "Number of batch dispatches");

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

    public Unit unit() {
        return unit;
    }

    public String description() {
        return description;
    }

}
