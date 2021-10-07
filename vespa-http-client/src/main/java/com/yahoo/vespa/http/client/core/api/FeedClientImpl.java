// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of FeedClient. It is a thin layer on top of multiClusterHandler and multiClusterResultAggregator.
 *
 * @author dybis
 */
public class FeedClientImpl implements FeedClient {

    private final Clock clock;
    private final OperationProcessor operationProcessor;
    private final long closeTimeoutMs;
    private final long sleepTimeMs = 500;

    public FeedClientImpl(SessionParams sessionParams,
                          ResultCallback resultCallback,
                          ScheduledThreadPoolExecutor timeoutExecutor,
                          Clock clock) {
        this.clock = clock;
        this.closeTimeoutMs = (10 + 3 * sessionParams.getConnectionParams().getMaxRetries()) *
                              (sessionParams.getFeedParams().getServerTimeout(TimeUnit.MILLISECONDS) +
                               sessionParams.getFeedParams().getClientTimeout(TimeUnit.MILLISECONDS));
        this.operationProcessor = new OperationProcessor(
                new IncompleteResultsThrottler(sessionParams.getThrottlerMinSize(),
                                               sessionParams.getClientQueueSize(),
                                               clock,
                                               new ThrottlePolicy()),
                resultCallback,
                sessionParams,
                timeoutExecutor,
                clock);
    }

    @Override
    public void stream(String documentId, String operationId, CharSequence documentData, Object context) {
        CharsetEncoder charsetEncoder = StandardCharsets.UTF_8.newEncoder();
        charsetEncoder.onMalformedInput(CodingErrorAction.REPORT);
        charsetEncoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        Document document = new Document(documentId, operationId, documentData, context, clock.instant());
        operationProcessor.sendDocument(document);
    }

    @Override
    public void close() {
        Instant lastOldestResultReceivedAt = Instant.now();
        Optional<String> oldestIncompleteId = operationProcessor.oldestIncompleteResultId();

        while (oldestIncompleteId.isPresent() && waitForOperations(lastOldestResultReceivedAt, sleepTimeMs, closeTimeoutMs)) {
            Optional<String> oldestIncompleteIdNow = operationProcessor.oldestIncompleteResultId();
            if ( ! oldestIncompleteId.equals(oldestIncompleteIdNow))
                lastOldestResultReceivedAt = Instant.now();
            oldestIncompleteId = oldestIncompleteIdNow;
        }
        operationProcessor.close();
    }

    @Override
    public String getStatsAsJson() {
        return operationProcessor.getStatsAsJson();
    }

    // On return value true, wait more. Public for testing.
    public static boolean waitForOperations(Instant lastResultReceived, long sleepTimeMs, long closeTimeoutMs) {
        if (lastResultReceived.plusMillis(closeTimeoutMs).isBefore(Instant.now())) {
            return false;
        }
        try {
            Thread.sleep(sleepTimeMs);
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

}
