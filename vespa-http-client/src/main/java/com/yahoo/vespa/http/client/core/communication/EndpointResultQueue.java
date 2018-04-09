// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.FeedEndpointException;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.operationProcessor.EndPointResultFactory;
import com.yahoo.vespa.http.client.core.EndpointResult;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Einar M R Rosenvinge
 */
class EndpointResultQueue {

    private static Logger log = Logger.getLogger(EndpointResultQueue.class.getName());
    private final OperationProcessor operationProcessor;
    private final Map<String, TimerFuture> futureByOperation = new HashMap<>();
    private final Endpoint endpoint;
    private final int clusterId;
    private final ScheduledThreadPoolExecutor timer;
    private final long totalTimeoutMs;

    EndpointResultQueue(
            OperationProcessor operationProcessor,
            Endpoint endpoint,
            int clusterId,
            ScheduledThreadPoolExecutor timer,
            long totalTimeoutMs) {
        this.operationProcessor = operationProcessor;
        this.endpoint = endpoint;
        this.clusterId = clusterId;
        this.timer = timer;
        this.totalTimeoutMs = totalTimeoutMs;
    }

    public synchronized void operationSent(String operationId) {
        DocumentTimerTask task = new DocumentTimerTask(operationId);
        ScheduledFuture<?> future = timer.schedule(task, totalTimeoutMs, TimeUnit.MILLISECONDS);
        futureByOperation.put(operationId, new TimerFuture(future));
    }

    public synchronized void failOperation(EndpointResult result, int clusterId) {
        resultReceived(result, clusterId, false);
    }

    public synchronized void resultReceived(EndpointResult result, int clusterId) {
        resultReceived(result, clusterId, true);
    }

    void onEndpointError(FeedEndpointException e) {
        operationProcessor.onEndpointError(e);
    }

    private synchronized void resultReceived(EndpointResult result, int clusterId, boolean duplicateGivesWarning) {
        operationProcessor.resultReceived(result, clusterId);

        TimerFuture timerFuture = futureByOperation.remove(result.getOperationId());
        if (timerFuture == null) {
            if (duplicateGivesWarning) {
                log.warning(
                        "Result for ID '" + result.getOperationId() + "' received from '" + endpoint
                         + "', but we have no record of a sent operation. Either something is wrong on the server side "
                         + "(bad VIP usage?), or we have somehow received duplicate results, "
                         + "or operation was received _after_ client-side timeout.");
            }
            return;
        }
        timerFuture.getFuture().cancel(false);
    }

    //Called only from ScheduledThreadPoolExecutor thread in DocumentTimerTask.run(), see below
    private synchronized void timeout(String operationId) {
        TimerFuture timerFuture = futureByOperation.remove(operationId);
        if (timerFuture == null) {
            log.finer(
                    "Timeout of operation '" + operationId + "', but operation "
                    + "not found in map. Result was probably received just-in-time from server, while timeout "
                    + "task could not be cancelled.");
            return;
        }
        EndpointResult endpointResult = EndPointResultFactory.createTransientError(
                endpoint, operationId, new RuntimeException("Timed out waiting for reply from server."));
        operationProcessor.resultReceived(endpointResult, clusterId);
    }

    public synchronized int getPendingSize() {
        return futureByOperation.values().size();
    }

    public synchronized void failPending(Exception exception) {
        for (Map.Entry<String, TimerFuture> timerFutureEntry : futureByOperation.entrySet()) {
            timerFutureEntry.getValue().getFuture().cancel(false);
            failedOperationId(timerFutureEntry.getKey(), exception);
        }
        futureByOperation.clear();
    }

    private synchronized void failedOperationId(String operationId, Exception exception) {
        EndpointResult endpointResult = EndPointResultFactory.createError(endpoint, operationId, exception);
        operationProcessor.resultReceived(endpointResult, clusterId);
    }

    private class DocumentTimerTask implements Runnable {
        private final String operationId;

        private DocumentTimerTask(String operationId) {
            this.operationId = operationId;
        }

        @Override
        public void run() {
            timeout(operationId);
        }
    }

    private class TimerFuture {
        private final ScheduledFuture<?> future;

        public TimerFuture(ScheduledFuture<?> future) {
            this.future = future;
        }
        private ScheduledFuture<?> getFuture() {
            return future;
        }
    }

}
