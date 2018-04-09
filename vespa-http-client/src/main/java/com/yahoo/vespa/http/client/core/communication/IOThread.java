// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.FeedConnectException;
import com.yahoo.vespa.http.client.FeedProtocolException;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.Exceptions;
import com.yahoo.vespa.http.client.core.operationProcessor.EndPointResultFactory;
import com.yahoo.vespa.http.client.core.EndpointResult;
import com.yahoo.vespa.http.client.core.ServerResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class for handling asynchronous feeding of new documents and processing of results.
 * 
 * @author Einar M R Rosenvinge
 */
class IOThread implements Runnable, AutoCloseable {

    private static Logger log = Logger.getLogger(IOThread.class.getName());
    private final Endpoint endpoint;
    private final GatewayConnection client;
    private final DocumentQueue documentQueue;
    private final EndpointResultQueue resultQueue;
    private final Thread thread;
    private final int clusterId;
    private final CountDownLatch running = new CountDownLatch(1);
    private final CountDownLatch stopSignal = new CountDownLatch(1);
    private final int maxChunkSizeBytes;
    private final int maxInFlightRequests;
    private final long localQueueTimeOut;
    private final GatewayThrottler gatewayThrottler;

    private enum ThreadState { DISCONNECTED, CONNECTED, SESSION_SYNCED };
    private final AtomicInteger wrongSessionDetectedCounter = new AtomicInteger(0);
    private final AtomicInteger wrongVersionDetectedCounter = new AtomicInteger(0);
    private final AtomicInteger problemStatusCodeFromServerCounter = new AtomicInteger(0);
    private final AtomicInteger executeProblemsCounter = new AtomicInteger(0);
    private final AtomicInteger docsReceivedCounter = new AtomicInteger(0);
    private final AtomicInteger statusReceivedCounter = new AtomicInteger(0);
    private final AtomicInteger pendingDocumentStatusCount = new AtomicInteger(0);
    private final AtomicInteger successfullHandshakes = new AtomicInteger(0);
    private final AtomicInteger lastGatewayProcessTimeMillis = new AtomicInteger(0);

    IOThread(
            EndpointResultQueue endpointResultQueue,
            GatewayConnection client,
            int clusterId,
            int maxChunkSizeBytes,
            int maxInFlightRequests,
            long localQueueTimeOut,
            DocumentQueue documentQueue,
            long maxSleepTimeMs) {
        this.documentQueue = documentQueue;
        this.endpoint = client.getEndpoint();
        this.client = client;
        this.resultQueue = endpointResultQueue;
        this.clusterId = clusterId;
        this.maxChunkSizeBytes = maxChunkSizeBytes;
        this.maxInFlightRequests = maxInFlightRequests;
        this.gatewayThrottler = new GatewayThrottler(maxSleepTimeMs);
        thread = new Thread(this, "IOThread " + endpoint);
        thread.setDaemon(true);
        this.localQueueTimeOut = localQueueTimeOut;
        thread.start();
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public static class ConnectionStats {
        public final int wrongSessionDetectedCounter;
        public final int wrongVersionDetectedCounter;
        public final int problemStatusCodeFromServerCounter;
        public final int executeProblemsCounter;
        public final int docsReceivedCounter;
        public final int statusReceivedCounter;
        public final int pendingDocumentStatusCount;
        public final int successfullHandshakes;
        public final int lastGatewayProcessTimeMillis;

        protected ConnectionStats(
                final int wrongSessionDetectedCounter,
                final int wrongVersionDetectedCounter,
                final int problemStatusCodeFromServerCounter,
                final int executeProblemsCounter,
                final int docsReceivedCounter,
                final int statusReceivedCounter,
                final int pendingDocumentStatusCount,
                final int successfullHandshakes,
                final int lastGatewayProcessTimeMillis) {
            this.wrongSessionDetectedCounter = wrongSessionDetectedCounter;
            this.wrongVersionDetectedCounter = wrongVersionDetectedCounter;
            this.problemStatusCodeFromServerCounter = problemStatusCodeFromServerCounter;
            this.executeProblemsCounter = executeProblemsCounter;
            this.docsReceivedCounter = docsReceivedCounter;
            this.statusReceivedCounter = statusReceivedCounter;
            this.pendingDocumentStatusCount = pendingDocumentStatusCount;
            this.successfullHandshakes = successfullHandshakes;
            this.lastGatewayProcessTimeMillis = lastGatewayProcessTimeMillis;
        }
    }

    /**
     * Returns a snapshot of counters. Threadsafe.
     */
    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(
                wrongSessionDetectedCounter.get(),
                wrongVersionDetectedCounter.get(),
                problemStatusCodeFromServerCounter.get(),
                executeProblemsCounter.get(),
                docsReceivedCounter.get(),
                statusReceivedCounter.get(),
                pendingDocumentStatusCount.get(),
                successfullHandshakes.get(),
                lastGatewayProcessTimeMillis.get());
    }

    @Override
    public void close() {
        documentQueue.close();
        if (stopSignal.getCount() == 0) {
            return;
        }

        stopSignal.countDown();
        log.finer("Closed called.");

        // Make a last attempt to get results from previous operations, we have already waited quite a bit before getting here.
        int size = resultQueue.getPendingSize();
        if (size > 0) {
            log.info("We have outstanding operations (" + size + ") , trying to fetch responses.");
            try {
                processResponse(client.drain());
            } catch (Throwable e) {
                log.log(Level.SEVERE, "Some failures while trying to get latest responses from vespa.", e);
            }
        }

        try {
            client.close();
        } finally {
            // If there is still documents in the queue, fail them.
            drainDocumentQueueWhenFailingPermanently(new Exception(
                    "Closed call, did not manage to process everything so failing this document."));
        }

        log.fine("Session to " + endpoint + " closed.");
    }


    public void post(final Document document) throws InterruptedException {
        documentQueue.put(document);
    }

    @Override
    public String toString() {
        return "I/O thread (for " + endpoint + ")";
    }


    List<Document> getNextDocsForFeeding(int maxWaitUnits, TimeUnit timeUnit) {
        final List<Document> docsForSendChunk = new ArrayList<>();
        int chunkSizeBytes = 0;
        try {
            drainFirstDocumentsInQueueIfOld();
            Document doc = documentQueue.poll(maxWaitUnits, timeUnit);
            if (doc != null) {
                docsForSendChunk.add(doc);
                chunkSizeBytes = doc.size();
            }
        } catch (InterruptedException ie) {
            log.fine("Got break signal while waiting for new documents to feed.");
            return docsForSendChunk;
        }
        int pendingSize = 1 + resultQueue.getPendingSize();
        // see if we can get more documents without blocking
        while (chunkSizeBytes < maxChunkSizeBytes && pendingSize < maxInFlightRequests) {
            drainFirstDocumentsInQueueIfOld();
            Document d = documentQueue.poll();
            if (d == null) {
                break;
            }
            docsForSendChunk.add(d);
            chunkSizeBytes += d.size();
            pendingSize++;
        }
        log.finest("Chunk has " + docsForSendChunk.size() + " docs with a size " + chunkSizeBytes + " bytes.");
        docsReceivedCounter.addAndGet(docsForSendChunk.size());
        return docsForSendChunk;
    }

    private void addDocumentsToResultQueue(List<Document> docs) {
        for (Document doc : docs) {
            resultQueue.operationSent(doc.getOperationId());
        }
    }

    private void markDocumentAsFailed(
            List<Document> docs, ServerResponseException servletException) {
        for (Document doc : docs) {
            resultQueue.failOperation(
                    EndPointResultFactory.createTransientError(
                            endpoint, doc.getOperationId(), servletException), clusterId);
        }
    }

    private InputStream sendAndReceive(List<Document> docs)
            throws IOException, ServerResponseException {
        try {
            // Post the new docs and get async responses for other posts.
            return client.writeOperations(docs);
        } catch (ServerResponseException ser) {
            markDocumentAsFailed(docs, ser);
            throw ser;
        } catch (Exception e) {
            markDocumentAsFailed(docs, new ServerResponseException(e.getMessage()));
            throw e;
        }
    }

    private static class ProcessResponse {
        private final int transitiveErrorCount;
        private final int processResultsCount;
        ProcessResponse(int transitiveErrorCount, int processResultsCount) {
            this.transitiveErrorCount = transitiveErrorCount;
            this.processResultsCount = processResultsCount;
        }
    }

    private ProcessResponse processResponse(InputStream serverResponse) throws IOException {
        final Collection<EndpointResult> endpointResults =
                EndPointResultFactory.createResult(endpoint, serverResponse);
        statusReceivedCounter.addAndGet(endpointResults.size());
        int transientErrors = 0;
        for (EndpointResult endpointResult : endpointResults) {
            if (endpointResult.getDetail().getResultType() == Result.ResultType.TRANSITIVE_ERROR) {
                transientErrors++;
            }
            resultQueue.resultReceived(endpointResult, clusterId);
        }
        return new ProcessResponse(transientErrors, endpointResults.size());
    }

    private ProcessResponse feedDocumentAndProcessResults(List<Document> docs)
            throws ServerResponseException, IOException {
        addDocumentsToResultQueue(docs);
        long startTime = System.currentTimeMillis();
        InputStream serverResponse = sendAndReceive(docs);

        ProcessResponse processResponse = processResponse(serverResponse);
        lastGatewayProcessTimeMillis.set((int) (System.currentTimeMillis() - startTime));
        return processResponse;
    }

    private ProcessResponse pullAndProcessData(int maxWaitTimeMilliSecs)
            throws ServerResponseException, IOException {
        final int pendingResultQueueSize = resultQueue.getPendingSize();
        pendingDocumentStatusCount.set(pendingResultQueueSize);

        List<Document> nextDocsForFeeding = (pendingResultQueueSize > maxInFlightRequests)
              ? new ArrayList<>()       // The queue is full, will not send more documents.
              : getNextDocsForFeeding(maxWaitTimeMilliSecs, TimeUnit.MILLISECONDS);


        if (nextDocsForFeeding.isEmpty() && pendingResultQueueSize == 0) {
            //we have no unfinished business with the server now.
            log.finest("No document awaiting feeding, not waiting for results.");
            return new ProcessResponse(0, 0);
        }
        log.finest("Awaiting " + pendingResultQueueSize + " results.");
        ProcessResponse processResponse = feedDocumentAndProcessResults(nextDocsForFeeding);
        if (pendingResultQueueSize > maxInFlightRequests && processResponse.processResultsCount == 0) {
            try {
                // Max outstanding document operations, no more results on server side, wait a bit
                // before asking again.
                Thread.sleep(300);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        return processResponse;
    }

    private ThreadState cycle(final ThreadState threadState) {
        switch(threadState) {
            case DISCONNECTED:
                try {
                    if (! client.connect()) {
                        log.log(Level.WARNING, "Connect returned null " + endpoint);
                        drainFirstDocumentsInQueueIfOld();
                        return ThreadState.DISCONNECTED;
                    }
                    return ThreadState.CONNECTED;
                } catch (Throwable throwable1) {
                    drainFirstDocumentsInQueueIfOld();
                    log.log(Level.INFO, "Connect did not work out " + endpoint, throwable1);
                    executeProblemsCounter.incrementAndGet();
                    return ThreadState.DISCONNECTED;
                }
            case CONNECTED:
                try {
                    client.handshake();
                    successfullHandshakes.getAndIncrement();
                } catch (ServerResponseException ser) {
                    executeProblemsCounter.incrementAndGet();
                    log.info("Handshake did not work out " + endpoint + ": " + Exceptions.toMessageString(ser));
                    drainFirstDocumentsInQueueIfOld();
                    resultQueue.onEndpointError(new FeedProtocolException(ser.getResponseCode(), ser.getResponseString(), ser, endpoint));
                    return ThreadState.CONNECTED;
                } catch (Throwable throwable) { // This cover IOException as well
                    executeProblemsCounter.incrementAndGet();
                    resultQueue.onEndpointError(new FeedConnectException(throwable, endpoint));
                    log.info("Problem with Handshake " + endpoint + ": " + Exceptions.toMessageString(throwable));
                    drainFirstDocumentsInQueueIfOld();
                    client.close();
                    return ThreadState.DISCONNECTED;
                }
                return ThreadState.SESSION_SYNCED;
            case SESSION_SYNCED:
                try {
                    ProcessResponse processResponse = pullAndProcessData(100);
                    gatewayThrottler.handleCall(processResponse.transitiveErrorCount);
                }
                catch (ServerResponseException ser) {
                    log.info("Problems while handing data over to gateway " + endpoint + ": " + Exceptions.toMessageString(ser));
                    return ThreadState.CONNECTED;
                }
                catch (Throwable e) { // Covers IOException as well
                    log.info("Problems while handing data over to gateway " + endpoint + ": " + Exceptions.toMessageString(e));
                    client.close();
                    return ThreadState.DISCONNECTED;
                }
                return ThreadState.SESSION_SYNCED;
            default: {
                log.severe("Should never get here.");
                client.close();
                return ThreadState.DISCONNECTED;
            }
        }
    }

    private void sleepIfProblemsGettingSyncedConnection(ThreadState newState, ThreadState oldState) {

        if (newState == ThreadState.SESSION_SYNCED) return;
        if (newState == ThreadState.CONNECTED && oldState == ThreadState.DISCONNECTED) return;
        try {
            // Take it easy we have problems getting a connection up.
            if (stopSignal.getCount() > 0 || !documentQueue.isEmpty()) {
                Thread.sleep(gatewayThrottler.distribute(3000));
            }
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void run() {
        ThreadState threadState = ThreadState.DISCONNECTED;
        while (stopSignal.getCount() > 0 || !documentQueue.isEmpty()) {
            ThreadState oldState = threadState;
            threadState = cycle(threadState);
            sleepIfProblemsGettingSyncedConnection(threadState, oldState);

        }
        log.finer(toString() + " exiting, documentQueue.size()=" + documentQueue.size());
        running.countDown();

    }

    private void drainFirstDocumentsInQueueIfOld() {
        while (true) {
            Optional<Document> document = documentQueue.pollDocumentIfTimedoutInQueue(localQueueTimeOut);
            if (! document.isPresent()) {
                return;
            }
            EndpointResult endpointResult = EndPointResultFactory.createTransientError(
                    endpoint, document.get().getOperationId(),
                    new Exception("Not sending document operation, timed out in queue after "
                            + document.get().timeInQueueMillis() + " ms."));
            resultQueue.failOperation(endpointResult, clusterId);
        }
    }

    private void drainDocumentQueueWhenFailingPermanently(Exception exception) {
        //first, clear sentOperations:
        resultQueue.failPending(exception);

        for (Document document : documentQueue.removeAllDocuments()) {
            EndpointResult endpointResult=
                    EndPointResultFactory.createError(endpoint, document.getOperationId(), exception);
            resultQueue.failOperation(endpointResult, clusterId);
        }
    }

}
