// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.api;

import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.ThrottlePolicy;
import com.yahoo.vespa.http.client.core.operationProcessor.IncompleteResultsThrottler;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Implementation of FeedClient. It is a thin layer on top of multiClusterHandler and multiClusterResultAggregator.
 * @author dybis
 */
public class FeedClientImpl implements FeedClient {

    private final OperationProcessor operationProcessor;

    public FeedClientImpl(
            SessionParams sessionParams, ResultCallback resultCallback, ScheduledThreadPoolExecutor timeoutExecutor) {

        this.operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(
                        sessionParams.getThrottlerMinSize(),
                        sessionParams.getClientQueueSize(),
                        ()->System.currentTimeMillis(),
                        new ThrottlePolicy()),
                resultCallback,
                sessionParams,
                timeoutExecutor);
    }

    @Override
    public void stream(String documentId, CharSequence documentData) {
        stream(documentId, documentData, null);
    }

    @Override
    public void stream(String documentId, CharSequence documentData, Object context) {
        CharsetEncoder charsetEncoder = StandardCharsets.UTF_8.newEncoder();
        charsetEncoder.onMalformedInput(CodingErrorAction.REPORT);
        charsetEncoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        final Document document = new Document(documentId, documentData, context);
        operationProcessor.sendDocument(document);
    }

    @Override
    public void close() {
        while (operationProcessor.getIncompleteResultQueueSize() > 0) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
        operationProcessor.close();
    }

    @Override
    public String getStatsAsJson() {
        return operationProcessor.getStatsAsJson();
    }
}
