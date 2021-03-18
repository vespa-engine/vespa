// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

/**
 * Name and dimensions for jdisc/container metrics
 *
 * @author bjorncs
 */
class MetricDefinitions {
    static final String NAME_DIMENSION = "serverName";
    static final String PORT_DIMENSION = "serverPort";
    static final String METHOD_DIMENSION = "httpMethod";
    static final String SCHEME_DIMENSION = "scheme";
    static final String REQUEST_TYPE_DIMENSION = "requestType";
    static final String CLIENT_IP_DIMENSION = "clientIp";
    static final String CLIENT_AUTHENTICATED_DIMENSION = "clientAuthenticated";
    static final String REQUEST_SERVER_NAME_DIMENSION = "requestServerName";
    static final String FILTER_CHAIN_ID_DIMENSION = "chainId";

    static final String NUM_OPEN_CONNECTIONS = "serverNumOpenConnections";
    static final String NUM_CONNECTIONS_OPEN_MAX = "serverConnectionsOpenMax";
    static final String CONNECTION_DURATION_MAX = "serverConnectionDurationMax";
    static final String CONNECTION_DURATION_MEAN = "serverConnectionDurationMean";
    static final String CONNECTION_DURATION_STD_DEV = "serverConnectionDurationStdDev";
    static final String NUM_PREMATURELY_CLOSED_CONNECTIONS = "jdisc.http.request.prematurely_closed";

    static final String NUM_BYTES_RECEIVED = "serverBytesReceived";
    static final String NUM_BYTES_SENT     = "serverBytesSent";

    static final String NUM_CONNECTIONS = "serverNumConnections";

    /* For historical reasons, these are all aliases for the same metric. 'jdisc.http' should ideally be the only one. */
    static final String JDISC_HTTP_REQUESTS = "jdisc.http.requests";
    static final String NUM_REQUESTS = "serverNumRequests";

    static final String NUM_SUCCESSFUL_RESPONSES = "serverNumSuccessfulResponses";
    static final String NUM_FAILED_RESPONSES = "serverNumFailedResponses";
    static final String NUM_SUCCESSFUL_WRITES = "serverNumSuccessfulResponseWrites";
    static final String NUM_FAILED_WRITES = "serverNumFailedResponseWrites";

    static final String TOTAL_SUCCESSFUL_LATENCY = "serverTotalSuccessfulResponseLatency";
    static final String TOTAL_FAILED_LATENCY = "serverTotalFailedResponseLatency";
    static final String TIME_TO_FIRST_BYTE = "serverTimeToFirstByte";

    static final String RESPONSES_1XX = "http.status.1xx";
    static final String RESPONSES_2XX = "http.status.2xx";
    static final String RESPONSES_3XX = "http.status.3xx";
    static final String RESPONSES_4XX = "http.status.4xx";
    static final String RESPONSES_5XX = "http.status.5xx";
    static final String RESPONSES_401 = "http.status.401";
    static final String RESPONSES_403 = "http.status.403";

    static final String STARTED_MILLIS = "serverStartedMillis";

    static final String URI_LENGTH = "jdisc.http.request.uri_length";
    static final String CONTENT_SIZE = "jdisc.http.request.content_size";

    static final String SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT = "jdisc.http.ssl.handshake.failure.missing_client_cert";
    static final String SSL_HANDSHAKE_FAILURE_EXPIRED_CLIENT_CERT = "jdisc.http.ssl.handshake.failure.expired_client_cert";
    static final String SSL_HANDSHAKE_FAILURE_INVALID_CLIENT_CERT = "jdisc.http.ssl.handshake.failure.invalid_client_cert";
    static final String SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS = "jdisc.http.ssl.handshake.failure.incompatible_protocols";
    static final String SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CIPHERS = "jdisc.http.ssl.handshake.failure.incompatible_ciphers";
    static final String SSL_HANDSHAKE_FAILURE_UNKNOWN = "jdisc.http.ssl.handshake.failure.unknown";

    static final String JETTY_THREADPOOL_MAX_THREADS = "jdisc.http.jetty.threadpool.thread.max";
    static final String JETTY_THREADPOOL_MIN_THREADS = "jdisc.http.jetty.threadpool.thread.min";
    static final String JETTY_THREADPOOL_RESERVED_THREADS = "jdisc.http.jetty.threadpool.thread.reserved";
    static final String JETTY_THREADPOOL_BUSY_THREADS = "jdisc.http.jetty.threadpool.thread.busy";
    static final String JETTY_THREADPOOL_IDLE_THREADS = "jdisc.http.jetty.threadpool.thread.idle";
    static final String JETTY_THREADPOOL_TOTAL_THREADS = "jdisc.http.jetty.threadpool.thread.total";
    static final String JETTY_THREADPOOL_QUEUE_SIZE = "jdisc.http.jetty.threadpool.queue.size";

    static final String FILTERING_REQUEST_HANDLED = "jdisc.http.filtering.request.handled";
    static final String FILTERING_REQUEST_UNHANDLED = "jdisc.http.filtering.request.unhandled";
    static final String FILTERING_RESPONSE_HANDLED = "jdisc.http.filtering.response.handled";
    static final String FILTERING_RESPONSE_UNHANDLED = "jdisc.http.filtering.response.unhandled";

    private MetricDefinitions() {}
}
