// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import ai.vespa.metrics.ContainerMetrics;

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
    static final String CLIENT_AUTHENTICATED_DIMENSION = "clientAuthenticated";
    static final String REQUEST_SERVER_NAME_DIMENSION = "requestServerName";
    static final String FILTER_CHAIN_ID_DIMENSION = "chainId";
    static final String PROTOCOL_DIMENSION = "protocol";
    static final String STATUS_CODE_DIMENSION = "statusCode";

    static final String NUM_OPEN_CONNECTIONS = ContainerMetrics.SERVER_NUM_OPEN_CONNECTIONS.baseName();
    static final String NUM_CONNECTIONS_OPEN_MAX = ContainerMetrics.SERVER_CONNECTIONS_OPEN_MAX.baseName();
    static final String CONNECTION_DURATION_MAX = ContainerMetrics.SERVER_CONNECTION_DURATION_MAX.baseName();
    static final String CONNECTION_DURATION_MEAN = ContainerMetrics.SERVER_CONNECTION_DURATION_MEAN.baseName();
    static final String CONNECTION_DURATION_STD_DEV = ContainerMetrics.SERVER_CONNECTION_DURATION_STD_DEV.baseName();
    static final String NUM_PREMATURELY_CLOSED_CONNECTIONS = ContainerMetrics.JDISC_HTTP_REQUEST_PREMATURELY_CLOSED.baseName();
    static final String REQUESTS_PER_CONNECTION = ContainerMetrics.JDISC_HTTP_REQUEST_REQUESTS_PER_CONNECTION.baseName();

    static final String NUM_BYTES_RECEIVED = ContainerMetrics.SERVER_BYTES_RECEIVED.baseName();
    static final String NUM_BYTES_SENT     = ContainerMetrics.SERVER_BYTES_SENT.baseName();

    static final String NUM_CONNECTIONS = ContainerMetrics.SERVER_NUM_CONNECTIONS.baseName();

    /* For historical reasons, these are all aliases for the same metric. 'jdisc.http' should ideally be the only one. */
    static final String JDISC_HTTP_REQUESTS = ContainerMetrics.JDISC_HTTP_REQUESTS.baseName();
    static final String NUM_REQUESTS = ContainerMetrics.SERVER_NUM_REQUESTS.baseName();

    static final String NUM_SUCCESSFUL_RESPONSES = ContainerMetrics.SERVER_NUM_SUCCESSFUL_RESPONSES.baseName();
    static final String NUM_FAILED_RESPONSES = ContainerMetrics.SERVER_NUM_FAILED_RESPONSES.baseName();
    static final String NUM_SUCCESSFUL_WRITES = ContainerMetrics.SERVER_NUM_SUCCESSFUL_RESPONSE_WRITES.baseName();
    static final String NUM_FAILED_WRITES = ContainerMetrics.SERVER_NUM_FAILED_RESPONSE_WRITES.baseName();

    static final String TOTAL_SUCCESSFUL_LATENCY = ContainerMetrics.SERVER_TOTAL_SUCCESSFUL_RESPONSE_LATENCY.baseName();
    static final String TOTAL_FAILED_LATENCY = ContainerMetrics.SERVER_TOTAL_FAILED_RESPONSE_LATENCY.baseName();
    static final String TIME_TO_FIRST_BYTE = ContainerMetrics.SERVER_TIME_TO_FIRST_BYTE.baseName();

    static final String RESPONSES_1XX = ContainerMetrics.HTTP_STATUS_1XX.baseName();
    static final String RESPONSES_2XX = ContainerMetrics.HTTP_STATUS_2XX.baseName();
    static final String RESPONSES_3XX = ContainerMetrics.HTTP_STATUS_3XX.baseName();
    static final String RESPONSES_4XX = ContainerMetrics.HTTP_STATUS_4XX.baseName();
    static final String RESPONSES_5XX = ContainerMetrics.HTTP_STATUS_5XX.baseName();

    static final String STARTED_MILLIS = ContainerMetrics.SERVER_STARTED_MILLIS.baseName();

    static final String URI_LENGTH = ContainerMetrics.JDISC_HTTP_REQUEST_URI_LENGTH.baseName();
    static final String CONTENT_SIZE = ContainerMetrics.JDISC_HTTP_REQUEST_CONTENT_SIZE.baseName();

    static final String SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT = ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT.baseName();
    static final String SSL_HANDSHAKE_FAILURE_EXPIRED_CLIENT_CERT = ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_EXPIRED_CLIENT_CERT.baseName();
    static final String SSL_HANDSHAKE_FAILURE_INVALID_CLIENT_CERT = ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INVALID_CLIENT_CERT.baseName();
    static final String SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS = ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS.baseName();
    static final String SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CIPHERS = ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CHIFERS.baseName();
    static final String SSL_HANDSHAKE_FAILURE_CONNECTION_CLOSED = ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_CONNECTION_CLOSED.baseName();
    static final String SSL_HANDSHAKE_FAILURE_UNKNOWN = ContainerMetrics.JDISC_HTTP_SSL_HANDSHAKE_FAILURE_UNKNOWN.baseName();

    static final String JETTY_THREADPOOL_MAX_THREADS = ContainerMetrics.JETTY_THREADPOOL_MAX_THREADS.baseName();
    static final String JETTY_THREADPOOL_MIN_THREADS = ContainerMetrics.JETTY_THREADPOOL_MIN_THREADS.baseName();
    static final String JETTY_THREADPOOL_RESERVED_THREADS = ContainerMetrics.JETTY_THREADPOOL_RESERVED_THREADS.baseName();
    static final String JETTY_THREADPOOL_BUSY_THREADS = ContainerMetrics.JETTY_THREADPOOL_BUSY_THREADS.baseName();
    static final String JETTY_THREADPOOL_IDLE_THREADS = ContainerMetrics.JETTY_THREADPOOL_IDLE_THREADS.baseName();
    static final String JETTY_THREADPOOL_TOTAL_THREADS = ContainerMetrics.JETTY_THREADPOOL_TOTAL_THREADS.baseName();
    static final String JETTY_THREADPOOL_QUEUE_SIZE = ContainerMetrics.JETTY_THREADPOOL_QUEUE_SIZE.baseName();

    static final String FILTERING_REQUEST_HANDLED = ContainerMetrics.JDISC_HTTP_FILTERING_REQUEST_HANDLED.baseName();
    static final String FILTERING_REQUEST_UNHANDLED = ContainerMetrics.JDISC_HTTP_FILTERING_REQUEST_UNHANDLED.baseName();
    static final String FILTERING_RESPONSE_HANDLED = ContainerMetrics.JDISC_HTTP_FILTERING_RESPONSE_HANDLED.baseName();
    static final String FILTERING_RESPONSE_UNHANDLED = ContainerMetrics.JDISC_HTTP_FILTERING_RESPONSE_UNHANDLED.baseName();

    private MetricDefinitions() {}
}
