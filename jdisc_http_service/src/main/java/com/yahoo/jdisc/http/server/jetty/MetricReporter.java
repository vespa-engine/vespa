// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Metric.Context;

import com.yahoo.jdisc.http.server.jetty.JettyHttpServer.Metrics;

import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Responsible for metric reporting for JDisc http request handler support.
 * @author Tony Vaagenes
 */
public class MetricReporter {
    private final Metric metric;
    private final Context context;

    private final long requestStartTime;

    //TODO: rename
    private final AtomicBoolean firstSetOfTimeToFirstByte = new AtomicBoolean(true);


    public MetricReporter(Metric metric, Context context, long requestStartTime) {
        this.metric = metric;
        this.context = context;
        this.requestStartTime = requestStartTime;
    }

    public void successfulWrite(int numBytes) {
        setTimeToFirstByteFirstTime();

        metric.add(Metrics.NUM_SUCCESSFUL_WRITES, 1, context);
        metric.set(Metrics.NUM_BYTES_SENT, numBytes, context);
    }

    private void setTimeToFirstByteFirstTime() {
        boolean isFirstWrite = firstSetOfTimeToFirstByte.getAndSet(false);
        if (isFirstWrite) {
            long timeToFirstByte = getRequestLatency();
            metric.set(Metrics.TIME_TO_FIRST_BYTE, timeToFirstByte, context);
        }
    }

    public void failedWrite() {
        metric.add(Metrics.NUM_FAILED_WRITES, 1, context);
    }

    public void successfulResponse() {
        setTimeToFirstByteFirstTime();

        long requestLatency = getRequestLatency();

        metric.set(Metrics.TOTAL_SUCCESSFUL_LATENCY, requestLatency, context);

        metric.add(Metrics.NUM_SUCCESSFUL_RESPONSES, 1, context);
    }

    public void failedResponse() {
        setTimeToFirstByteFirstTime();

        metric.set(Metrics.TOTAL_FAILED_LATENCY, getRequestLatency(), context);
        metric.add(Metrics.NUM_FAILED_RESPONSES, 1, context);
    }

    public void prematurelyClosed() {
        metric.add(Metrics.NUM_PREMATURELY_CLOSED_CONNECTIONS, 1, context);
    }

    public void successfulRead(int bytes_received) {
        metric.set(JettyHttpServer.Metrics.NUM_BYTES_RECEIVED, bytes_received, context);
    }

    private long getRequestLatency() {
        return System.currentTimeMillis() - requestStartTime;
    }

    public void uriLength(int length) {
        metric.set(Metrics.URI_LENGTH, length, context);
    }

    public void contentSize(int size) {
        metric.set(Metrics.CONTENT_SIZE, size, context);
    }
}
