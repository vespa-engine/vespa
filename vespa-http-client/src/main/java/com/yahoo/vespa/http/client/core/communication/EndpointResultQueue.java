// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.FeedEndpointException;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.EndpointResult;
import com.yahoo.vespa.http.client.core.operationProcessor.EndPointResultFactory;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * The shared queue of operation results.
 * This is multithread safe.
 *
 * @author Einar M R Rosenvinge
 */
class EndpointResultQueue {

    private static final Logger log = Logger.getLogger(EndpointResultQueue.class.getName());
    private final OperationProcessor operationProcessor;

    private final Map<String, InflightOperation> inflightOperations = new HashMap<>();

    private final Endpoint endpoint;
    private final int clusterId;
    private final ScheduledThreadPoolExecutor timer;
    private final long totalTimeoutMs;

    EndpointResultQueue(OperationProcessor operationProcessor,
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

    public synchronized void operationSent(String operationId, GatewayConnection connection) {
        DocumentTimerTask task = new DocumentTimerTask(operationId);
        ScheduledFuture<?> future = timer.schedule(task, totalTimeoutMs, TimeUnit.MILLISECONDS);
        inflightOperations.put(operationId, new InflightOperation(future, connection));
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
        InflightOperation operation = inflightOperations.remove(result.getOperationId());
        if (operation == null) {
            if (duplicateGivesWarning) {
                log.warning("Result for ID '" + result.getOperationId() + "' received from '" + endpoint +
                            "', but we have no record of a sent operation. Either something is wrong on the server side " +
                            "(bad VIP usage?), or we have somehow received duplicate results, " +
                            "or operation was received _after_ client-side timeout.");
            }
            return;
        }
        operation.future.cancel(false);
    }

    /** Called only from ScheduledThreadPoolExecutor thread in DocumentTimerTask.run(), see below */
    private synchronized void timeout(String operationId) {
        InflightOperation operation = inflightOperations.remove(operationId);
        if (operation == null) {
            log.finer("Timeout of operation '" + operationId + "', but operation " +
                      "not found in map. Result was probably received just-in-time from server, while timeout " +
                      "task could not be cancelled.");
            return;
        }
        EndpointResult endpointResult = EndPointResultFactory.createTransientError(
                endpoint, operationId, new RuntimeException("Timed out waiting for reply from server."));
        operationProcessor.resultReceived(endpointResult, clusterId);
    }

    public synchronized int getPendingSize() {
        return inflightOperations.values().size();
    }

    public synchronized void failPending(Exception exception) {
        inflightOperations.forEach((operationId, operation) -> {
            operation.future.cancel(false);
            EndpointResult result = EndPointResultFactory.createError(endpoint, operationId, exception);
            operationProcessor.resultReceived(result, clusterId);
        });
        inflightOperations.clear();
    }

    public synchronized boolean hasInflightOperations(GatewayConnection connection) {
        return inflightOperations.entrySet().stream()
                .anyMatch(entry -> entry.getValue().connection.equals(connection));
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

    private static class InflightOperation {
        final ScheduledFuture<?> future;
        final GatewayConnection connection;

        InflightOperation(ScheduledFuture<?> future, GatewayConnection connection) {
            this.future = future;
            this.connection = connection;
        }
    }
}
