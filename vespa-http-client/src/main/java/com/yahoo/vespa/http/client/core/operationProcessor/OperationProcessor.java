// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.google.common.collect.ArrayListMultimap;
import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.FeedEndpointException;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.SessionParams;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.communication.EndpointIOException;
import com.yahoo.vespa.http.client.core.EndpointResult;
import com.yahoo.vespa.http.client.core.Exceptions;
import com.yahoo.vespa.http.client.core.communication.ClusterConnection;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Merges several endpointResult into one Result and does the callback.
 *
 * @author dybis
 */
public class OperationProcessor {

    private static final Logger log = Logger.getLogger(OperationProcessor.class.getName());
    private final Map<String, DocumentSendInfo> docSendInfoByOperationId = new HashMap<>();
    private final ArrayListMultimap<String, Document> blockedDocumentsByDocumentId = ArrayListMultimap.create();
    private final Set<String> inflightDocumentIds = new HashSet<>();
    private final int numDestinations;
    private final FeedClient.ResultCallback resultCallback;
    private final Object monitor = new Object();
    private final IncompleteResultsThrottler incompleteResultsThrottler;
    // Position in the array is cluster ID.
    private final List<ClusterConnection> clusters = new ArrayList<>();
    private final ScheduledThreadPoolExecutor timeoutExecutor;
    private final OperationStats operationStats;
    private final int maxRetries;
    private final long minTimeBetweenRetriesMs;
    private final Random random = new SecureRandom();
    private final int traceEveryXOperation;
    private final boolean blockOperationsToSameDocument;
    private int traceCounter = 0;
    private final boolean traceToStderr;
    private final String clientId = new BigInteger(130, random).toString(32);;

    public OperationProcessor(
            IncompleteResultsThrottler incompleteResultsThrottler,
            FeedClient.ResultCallback resultCallback,
            SessionParams sessionParams,
            ScheduledThreadPoolExecutor timeoutExecutor) {
        this.numDestinations = sessionParams.getClusters().size();
        this.resultCallback = resultCallback;
        this.incompleteResultsThrottler = incompleteResultsThrottler;
        this.timeoutExecutor = timeoutExecutor;
        this.blockOperationsToSameDocument = sessionParams.getConnectionParams().isEnableV3Protocol();

        if (sessionParams.getClusters().isEmpty()) {
            throw new IllegalArgumentException("Cannot feed to 0 clusters.");
        }

        for (Cluster cluster : sessionParams.getClusters()) {
            if (cluster.getEndpoints().isEmpty()) {
                throw new IllegalArgumentException("Cannot feed to empty cluster.");
            }
        }

        for (int i = 0; i < sessionParams.getClusters().size(); i++) {
            Cluster cluster = sessionParams.getClusters().get(i);

            clusters.add(new ClusterConnection(
                    this,
                    sessionParams.getFeedParams(),
                    sessionParams.getConnectionParams(),
                    sessionParams.getErrorReport(),
                    cluster,
                    i,
                    sessionParams.getClientQueueSize() / sessionParams.getClusters().size(),
                    timeoutExecutor));

            }
        operationStats = new OperationStats(sessionParams, clusters, incompleteResultsThrottler);
        maxRetries = sessionParams.getConnectionParams().getMaxRetries();
        minTimeBetweenRetriesMs = sessionParams.getConnectionParams().getMinTimeBetweenRetriesMs();
        traceEveryXOperation = sessionParams.getConnectionParams().getTraceEveryXOperation();
        traceToStderr = sessionParams.getConnectionParams().getPrintTraceToStdErr();
    }

    public int getIncompleteResultQueueSize() {
        synchronized (monitor) {
            return docSendInfoByOperationId.size();
        }
    }

    public String getClientId() {
        return clientId;
    }

    private boolean retriedThis(EndpointResult endpointResult, DocumentSendInfo documentSendInfo, int clusterId) {
        final Result.Detail detail = endpointResult.getDetail();
        // If success, no retries to do.
        if (detail.getResultType() == Result.ResultType.OPERATION_EXECUTED) {
            return false;
        }

        int retries = documentSendInfo.incRetries(clusterId, detail);
        if (retries > maxRetries) {
            return false;
        }

        String exceptionMessage = detail.getException() == null ? "" : detail.getException().getMessage();
        if (exceptionMessage == null) {
            exceptionMessage = "";
        }
        // TODO: Return proper error code in structured data in next version of internal API.
        // Error codes from messagebus/src/cpp/messagebus/errorcode.h
        boolean retryThisOperation =
                detail.getResultType() == Result.ResultType.TRANSITIVE_ERROR ||
                exceptionMessage.contains("SEND_QUEUE_CLOSED") ||
                exceptionMessage.contains("ILLEGAL_ROUTE") ||
                exceptionMessage.contains("NO_SERVICES_FOR_ROUTE") ||
                exceptionMessage.contains("NETWORK_ERROR") ||
                exceptionMessage.contains("SEQUENCE_ERROR") ||
                exceptionMessage.contains("NETWORK_SHUTDOWN") ||
                exceptionMessage.contains("TIMEOUT");

        if (retryThisOperation) {
            int waitTime = (int) (minTimeBetweenRetriesMs * (1 + random.nextDouble() / 3));
            log.finest("Retrying due to " + detail.toString() + " attempt " + retries
                    + " in " + waitTime + " ms.");
            timeoutExecutor.schedule(
                    () -> postToCluster(clusters.get(clusterId), documentSendInfo.getDocument()),
                    waitTime,
                    TimeUnit.MILLISECONDS);
            return true;
        }

        return false;
    }

    private Result process(EndpointResult endpointResult, int clusterId) {

        synchronized (monitor) {
            if (!docSendInfoByOperationId.containsKey(endpointResult.getOperationId())) {
                log.finer("Received out-of-order or too late result, discarding: " + endpointResult);
                return null;
            }
            DocumentSendInfo documentSendInfo = docSendInfoByOperationId.get(endpointResult.getOperationId());

            if (retriedThis(endpointResult, documentSendInfo, clusterId)) {
                return null;
            }

            if (!documentSendInfo.addIfNotAlreadyThere(endpointResult.getDetail(), clusterId)) {
                // Duplicate message, we have seen this operation before.
                return null;
            }

            // Is this the last operation we are waiting for?
            if (documentSendInfo.detailCount() != numDestinations) {
                return null;
            }

            Result result = documentSendInfo.createResult();
            docSendInfoByOperationId.remove(endpointResult.getOperationId());

            String documentId = documentSendInfo.getDocument().getDocumentId();
            /**
             * If we got a pending operation against this document
             * dont't remove it from inflightDocuments and send blocked document operation
             */
            List<Document> blockedDocuments = blockedDocumentsByDocumentId.get(documentId);
            if (blockedDocuments.isEmpty()) {
                inflightDocumentIds.remove(documentId);
            } else {
                sendToClusters(blockedDocuments.remove(0));
            }
            return result;
        }
    }

    public void resultReceived(EndpointResult endpointResult, int clusterId) {
        final Result result = process(endpointResult, clusterId);

        if (result != null) {
            incompleteResultsThrottler.resultReady(result.isSuccess());
            resultCallback.onCompletion(result.getDocumentId(), result);
            if (traceToStderr && result.hasLocalTrace()) {
                System.err.println(result.toString());
            }
        }
    }

    public void onEndpointError(FeedEndpointException e) {
        resultCallback.onEndpointException(e);
    }

    public List<Exception> closeClusters() {
        List<Exception> exceptions = new ArrayList<>();
        // first, close cluster sessions and allow connections to drain normally
        for (ClusterConnection cluster : clusters) {
            try {
                cluster.close();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        return exceptions;
    }

    public void sendDocument(Document document) {
        incompleteResultsThrottler.operationStart();

        synchronized (monitor) {
            if (blockOperationsToSameDocument && inflightDocumentIds.contains(document.getDocumentId())) {
                blockedDocumentsByDocumentId.put(document.getDocumentId(), document);
                return;
            }
            inflightDocumentIds.add(document.getDocumentId());
        }

        sendToClusters(document);
    }

    private void sendToClusters(Document document) {

        synchronized (monitor) {
            boolean traceThisDoc = traceEveryXOperation > 0 && traceCounter++ % traceEveryXOperation == 0;
            docSendInfoByOperationId.put(document.getOperationId(), new DocumentSendInfo(document, traceThisDoc));
        }

        for (ClusterConnection clusterConnection : clusters) {
            postToCluster(clusterConnection, document);
        }
    }

    private void postToCluster(ClusterConnection clusterConnection, Document document) {
        try {
            clusterConnection.post(document);
        } catch (EndpointIOException eio) {
            resultReceived(EndPointResultFactory.createError(eio.getEndpoint(),
                                                             document.getOperationId(),
                                                             eio),
                                                             clusterConnection.getClusterId());
        }
    }

    public String getStatsAsJson() {
        return operationStats.getStatsAsJson();
    }

    public void close() {
        List<Exception> exceptions = closeClusters();
        try {
            closeExecutor();
        } catch (InterruptedException e) {
            exceptions.add(e);
        }

        if (exceptions.isEmpty()) {
            return;
        }
        if (exceptions.size() == 1) {
            if (exceptions.get(0) instanceof RuntimeException) {
                throw (RuntimeException) exceptions.get(0);
            } else {
                throw new RuntimeException(exceptions.get(0));
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("Exception thrown while closing one or more clusters: ");
        for (int i = 0; i < exceptions.size(); i++) {
            Exception e = exceptions.get(i);
            b.append(Exceptions.toMessageString(e));
            if (i != (exceptions.size() - 1)) {
                b.append(", ");
            }
        }
        throw new RuntimeException(b.toString(), exceptions.get(0));
    }

    private void closeExecutor() throws InterruptedException {
        log.log(Level.FINE, "Shutting down timeout executor.");
        timeoutExecutor.shutdownNow();

        log.log(Level.FINE, "Awaiting termination of already running timeout tasks.");
        if (! timeoutExecutor.awaitTermination(300, TimeUnit.SECONDS)) {
            log.severe("Did not manage to shut down the executors within 300 secs, system stuck?");
            throw new RuntimeException("Did not manage to shut down retry threads. Please report problem.");
        }
    }
}
