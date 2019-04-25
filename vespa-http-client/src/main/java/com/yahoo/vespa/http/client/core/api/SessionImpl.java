// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.api;

import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.Session;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.ThrottlePolicy;
import com.yahoo.vespa.http.client.core.operationProcessor.IncompleteResultsThrottler;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;

import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * This class wires up the Session API using MultiClusterHandler and MultiClusterSessionOutputStream.
 */
public class SessionImpl implements Session {

    private final OperationProcessor operationProcessor;
    private final BlockingQueue<Result> resultQueue = new LinkedBlockingQueue<>();


    public SessionImpl(SessionParams sessionParams, ScheduledThreadPoolExecutor timeoutExecutor) {
        this.operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(
                        sessionParams.getThrottlerMinSize(),
                        sessionParams.getClientQueueSize(),
                        ()->System.currentTimeMillis(),
                        new ThrottlePolicy()),
                new FeedClient.ResultCallback() {
                    @Override
                    public void onCompletion(String docId, Result documentResult) {
                        resultQueue.offer(documentResult);
                    }
                },
                sessionParams,
                timeoutExecutor);
    }

    @Override
    public OutputStream stream(CharSequence documentId) {
        return new MultiClusterSessionOutputStream(documentId, operationProcessor, null);
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
