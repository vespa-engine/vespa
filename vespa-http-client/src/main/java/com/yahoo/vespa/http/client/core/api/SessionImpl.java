// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.api;

import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.ThrottlePolicy;
import com.yahoo.vespa.http.client.core.operationProcessor.IncompleteResultsThrottler;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;

import java.io.OutputStream;
import java.time.Clock;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * This class wires up the Session API using MultiClusterHandler and MultiClusterSessionOutputStream.
 *
 * @deprecated
 */
@Deprecated // TODO: Remove on Vespa 8
public class SessionImpl implements com.yahoo.vespa.http.client.Session {

    private final OperationProcessor operationProcessor;
    private final BlockingQueue<Result> resultQueue = new LinkedBlockingQueue<>();
    private final Clock clock;

    public SessionImpl(SessionParams sessionParams, ScheduledThreadPoolExecutor timeoutExecutor, Clock clock) {
        this.clock = clock;
        this.operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(
                        sessionParams.getThrottlerMinSize(),
                        sessionParams.getClientQueueSize(),
                        clock,
                        new ThrottlePolicy()),
                new FeedClient.ResultCallback() {
                    @Override
                    public void onCompletion(String docId, Result documentResult) {
                        resultQueue.offer(documentResult);
                    }
                },
                sessionParams,
                timeoutExecutor,
                clock);
    }

    @Override
    public OutputStream stream(CharSequence documentId) {
        return new MultiClusterSessionOutputStream(documentId, operationProcessor, null, clock);
    }

    @Override
    public BlockingQueue<Result> results() {
        return resultQueue;
    }

    @Override
    public void close() {
        operationProcessor.close();
    }

    @Override
    public String getStatsAsJson() {
        return operationProcessor.getStatsAsJson();
    }

    // For testing only (legacy tests).
    public int getIncompleteResultQueueSize() {
        return operationProcessor.getIncompleteResultQueueSize();
    }

}
