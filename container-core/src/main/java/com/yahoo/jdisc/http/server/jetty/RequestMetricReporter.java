// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Metric.Context;

import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Responsible for metric reporting for JDisc http request handler support.
 * @author Tony Vaagenes
 */
class RequestMetricReporter {
    private final Metric metric;
    private final Context context;

    private final long requestStartTime;

    //TODO: rename
    private final AtomicBoolean firstSetOfTimeToFirstByte = new AtomicBoolean(true);


    RequestMetricReporter(Metric metric, Context context, long requestStartTime) {
        this.metric = metric;
        this.context = context;
        this.requestStartTime = requestStartTime;
    }

    void successfulWrite(int numBytes) {
        setTimeToFirstByteFirstTime();

        metric.add(MetricDefinitions.NUM_SUCCESSFUL_WRITES, 1, context);
        metric.set(MetricDefinitions.NUM_BYTES_SENT, numBytes, context);
    }

    private void setTimeToFirstByteFirstTime() {
        boolean isFirstWrite = firstSetOfTimeToFirstByte.getAndSet(false);
        if (isFirstWrite) {
            long timeToFirstByte = getRequestLatency();
            metric.set(MetricDefinitions.TIME_TO_FIRST_BYTE, timeToFirstByte, context);
        }
    }

    void failedWrite() {
        metric.add(MetricDefinitions.NUM_FAILED_WRITES, 1, context);
    }

    void successfulResponse() {
        setTimeToFirstByteFirstTime();

        long requestLatency = getRequestLatency();

        metric.set(MetricDefinitions.TOTAL_SUCCESSFUL_LATENCY, requestLatency, context);

        metric.add(MetricDefinitions.NUM_SUCCESSFUL_RESPONSES, 1, context);
    }

    void failedResponse() {
        setTimeToFirstByteFirstTime();

        metric.set(MetricDefinitions.TOTAL_FAILED_LATENCY, getRequestLatency(), context);
        metric.add(MetricDefinitions.NUM_FAILED_RESPONSES, 1, context);
    }

    void prematurelyClosed() {
        metric.add(MetricDefinitions.NUM_PREMATURELY_CLOSED_CONNECTIONS, 1, context);
    }

    void successfulRead(int bytes_received) {
        metric.set(MetricDefinitions.NUM_BYTES_RECEIVED, bytes_received, context);
    }

    private long getRequestLatency() {
        return System.currentTimeMillis() - requestStartTime;
    }

    void uriLength(int length) {
        metric.set(MetricDefinitions.URI_LENGTH, length, context);
    }

    void contentSize(int size) {
        metric.set(MetricDefinitions.CONTENT_SIZE, size, context);
    }
}
