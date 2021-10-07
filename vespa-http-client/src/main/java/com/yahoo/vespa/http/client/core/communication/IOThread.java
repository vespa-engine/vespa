// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.FeedConnectException;
import com.yahoo.vespa.http.client.FeedProtocolException;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.EndpointResult;
import com.yahoo.vespa.http.client.core.Exceptions;
import com.yahoo.vespa.http.client.core.ServerResponseException;
import com.yahoo.vespa.http.client.core.operationProcessor.EndPointResultFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread which feeds document operations asynchronously and processes the results.
 * 
 * @author Einar M R Rosenvinge
 */
public class IOThread implements Runnable, AutoCloseable {

    private static final Logger log = Logger.getLogger(IOThread.class.getName());

    private final Endpoint endpoint;
    private final GatewayConnectionFactory connectionFactory;
    private final DocumentQueue documentQueue;
    private final EndpointResultQueue resultQueue;

    /** The thread running this, or null if it does not run a thread (meaning tick() must be called from the outside) */
    private final Thread thread;
    private final int clusterId;
    private final CountDownLatch running = new CountDownLatch(1);
    private final CountDownLatch stopSignal = new CountDownLatch(1);
    private final int maxChunkSizeBytes;
    private final int maxInFlightRequests;
    private final Duration localQueueTimeOut;
    private final GatewayThrottler gatewayThrottler;
    private final Duration connectionTimeToLive;
    private final long pollIntervalUS;
    private final Clock clock;
    private final Random random = new Random();
    private final OldConnectionsDrainer oldConnectionsDrainer;

    private volatile GatewayConnection currentConnection;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    private enum ConnectionState { DISCONNECTED, CONNECTED, SESSION_SYNCED };
    private final AtomicInteger wrongSessionDetectedCounter = new AtomicInteger(0);
    private final AtomicInteger wrongVersionDetectedCounter = new AtomicInteger(0);
    private final AtomicInteger problemStatusCodeFromServerCounter = new AtomicInteger(0);
    private final AtomicInteger executeProblemsCounter = new AtomicInteger(0);
    private final AtomicInteger docsReceivedCounter = new AtomicInteger(0);
    private final AtomicInteger statusReceivedCounter = new AtomicInteger(0);
    private final AtomicInteger pendingDocumentStatusCount = new AtomicInteger(0);
    private final AtomicInteger successfulHandshakes = new AtomicInteger(0);
    private final AtomicInteger lastGatewayProcessTimeMillis = new AtomicInteger(0);

    IOThread(ThreadGroup ioThreadGroup,
             Endpoint endpoint,
             EndpointResultQueue endpointResultQueue,
             GatewayConnectionFactory connectionFactory,
             int clusterId,
             int maxChunkSizeBytes,
             int maxInFlightRequests,
             Duration localQueueTimeOut,
             DocumentQueue documentQueue,
             long maxSleepTimeMs,
             Duration connectionTimeToLive,
             boolean runThreads,
             double idlePollFrequency,
             Clock clock) {
        this.endpoint = endpoint;
        this.documentQueue = documentQueue;
        this.connectionFactory = connectionFactory;
        this.currentConnection = connectionFactory.newConnection();
        this.resultQueue = endpointResultQueue;
        this.clusterId = clusterId;
        this.maxChunkSizeBytes = maxChunkSizeBytes;
        this.maxInFlightRequests = maxInFlightRequests;
        this.connectionTimeToLive = connectionTimeToLive;
        this.gatewayThrottler = new GatewayThrottler(maxSleepTimeMs);
        this.pollIntervalUS = Math.max(1000, (long)(1000000.0/Math.max(0.1, idlePollFrequency))); // ensure range [1ms, 10s]
        this.clock = clock;
        this.localQueueTimeOut = localQueueTimeOut;
        this.oldConnectionsDrainer = new OldConnectionsDrainer(endpoint,
                                                               clusterId,
                                                               Duration.ofMillis(pollIntervalUS/1000),
                                                               connectionTimeToLive,
                                                               localQueueTimeOut,
                                                               statusReceivedCounter,
                                                               resultQueue,
                                                               stopSignal,
                                                               clock);
        if (runThreads) {
            this.thread = new Thread(ioThreadGroup, this, "IOThread " + endpoint);
            thread.setDaemon(true);
            thread.start();
            Thread thread = new Thread(ioThreadGroup, oldConnectionsDrainer, "IOThread " + endpoint + " drainer");
            thread.setDaemon(true);
            thread.start();
        }
        else {
            this.thread = null;
        }
    }

    public Endpoint getEndpoint() {
        return endpoint;
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
                successfulHandshakes.get(),
                lastGatewayProcessTimeMillis.get());
    }

    @Override
    public void close() {
        documentQueue.close();
        if (stopSignal.getCount() == 0) return;

        stopSignal.countDown();
        log.finer("Closed called.");

        oldConnectionsDrainer.close();

        // Make a last attempt to get results from previous operations, we have already waited quite a bit before getting here.
        int size = resultQueue.getPendingSize();
        if (size > 0) {
            log.info("We have outstanding operations (" + size + ") , trying to fetch responses.");
            try {
                processResponse(currentConnection.drain());
            } catch (Throwable e) {
                log.log(Level.SEVERE, "Some failures while trying to get latest responses from vespa.", e);
            }
        }

        try {
            currentConnection.close();
        } finally {
            // If there is still documents in the queue, fail them.
            drainDocumentQueueWhenFailingPermanently(new Exception("Closed call, did not manage to process everything so failing this document."));
        }

        log.fine("Session to " + endpoint + " closed.");
    }

    /** For testing only */
    public void post(Document document) throws InterruptedException {
        documentQueue.put(document, true);
    }

    @Override
    public String toString() {
        return "I/O thread (for " + endpoint + ")";
    }

    List<Document> getNextDocsForFeeding(long maxWaitUnits, TimeUnit timeUnit) {
        List<Document> docsForSendChunk = new ArrayList<>();
        int chunkSizeBytes = 0;
        try {
            drainFirstDocumentsInQueueIfOld();
            Document doc = thread != null ? documentQueue.poll(maxWaitUnits, timeUnit) : documentQueue.poll();
            if (doc != null) {
                docsForSendChunk.add(doc);
                chunkSizeBytes = doc.size();
            }
        } catch (InterruptedException ie) {
            log.fine("Got break signal while waiting for new documents to feed");
            return docsForSendChunk;
        }
        int pendingSize = 1 + resultQueue.getPendingSize();

        // see if we can get more documents without blocking
        // slightly randomize how much is taken to avoid harmonic interactions leading
        // to some threads consistently taking more than others
        int thisMaxChunkSizeBytes = randomize(maxChunkSizeBytes);
        int thisMaxInFlightRequests = randomize(maxInFlightRequests);
        while (chunkSizeBytes < thisMaxChunkSizeBytes && pendingSize < thisMaxInFlightRequests) {
            drainFirstDocumentsInQueueIfOld();
            Document document = documentQueue.poll();
            if (document == null) break;
            docsForSendChunk.add(document);
            chunkSizeBytes += document.size();
            pendingSize++;
        }
        if (log.isLoggable(Level.FINEST))
            log.finest("Chunk has " + docsForSendChunk.size() + " docs with a size " + chunkSizeBytes + " bytes");
        docsReceivedCounter.addAndGet(docsForSendChunk.size());
        return docsForSendChunk;
    }

    private int randomize(int limit) {
        double multiplier = 0.75 + 0.25 * random.nextDouble();
        return Math.max(1, (int)(limit * multiplier));
    }

    private void addDocumentsToResultQueue(List<Document> docs) {
        for (Document doc : docs) {
            resultQueue.operationSent(doc.getOperationId(), currentConnection);
        }
    }

    private void markDocumentAsFailed(List<Document> docs, ServerResponseException servletException) {
        for (Document doc : docs) {
            resultQueue.failOperation(EndPointResultFactory.createTransientError(endpoint,
                                                                                 doc.getOperationId(),
                                                                                 servletException),
                                      clusterId);
        }
    }

    private InputStream sendAndReceive(List<Document> docs) throws IOException, ServerResponseException {
        try {
            // Post the new docs and get async responses for other posts.
            return currentConnection.write(docs);
        } catch (ServerResponseException ser) {
            markDocumentAsFailed(docs, ser);
            throw ser;
        } catch (Exception e) {
            markDocumentAsFailed(docs, new ServerResponseException(Exceptions.toMessageString(e)));
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
        return processResponse(serverResponse, endpoint, clusterId, statusReceivedCounter, resultQueue);
    }

    private static ProcessResponse processResponse(InputStream serverResponse,
                                                   Endpoint endpoint,
                                                   int clusterId,
                                                   AtomicInteger statusReceivedCounter,
                                                   EndpointResultQueue resultQueue) throws IOException {
        Collection<EndpointResult> endpointResults = EndPointResultFactory.createResult(endpoint, serverResponse);
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
        long startTime = clock.millis();
        InputStream serverResponse = sendAndReceive(docs);

        ProcessResponse processResponse = processResponse(serverResponse);
        lastGatewayProcessTimeMillis.set((int) (clock.millis() - startTime));
        return processResponse;
    }

    private ProcessResponse pullAndProcessData(long maxWaitTimeUS) throws ServerResponseException, IOException {
        int pendingResultQueueSize = resultQueue.getPendingSize();
        pendingDocumentStatusCount.set(pendingResultQueueSize);

        List<Document> nextDocsForFeeding = (pendingResultQueueSize > maxInFlightRequests)
                                            ? new ArrayList<>() // The queue is full, will not send more documents
                                            : getNextDocsForFeeding(maxWaitTimeUS, TimeUnit.MICROSECONDS);

        if (nextDocsForFeeding.isEmpty() && pendingResultQueueSize == 0) {
            //we have no unfinished business with the server now.
            log.finest("No document awaiting feeding, not waiting for results.");
            return new ProcessResponse(0, 0);
        }
        log.finest("Awaiting " + pendingResultQueueSize + " results.");
        ProcessResponse processResponse = feedDocumentAndProcessResults(nextDocsForFeeding);

        if (pendingResultQueueSize > maxInFlightRequests && processResponse.processResultsCount == 0) {
            try {
                // Max outstanding document operations, no more results on server side, wait a bit before asking again
                Thread.sleep(300);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        return processResponse;
    }

    /** Given a current connection state, take the appropriate action and return the resulting new connection state */
    private ConnectionState cycle(ConnectionState connectionState) {
        switch(connectionState) {
            case DISCONNECTED:
                try {
                    if (! currentConnection.connect()) {
                        log.log(Level.WARNING, "Could not connect to endpoint: '" + endpoint + "'. Will re-try.");
                        drainFirstDocumentsInQueueIfOld();
                        return ConnectionState.DISCONNECTED;
                    }
                    return ConnectionState.CONNECTED;
                } catch (Throwable throwable1) {
                    drainFirstDocumentsInQueueIfOld();

                    log.log(Level.INFO, "Failed connecting to endpoint: '" + endpoint + "'. Will re-try connecting.",
                            throwable1);
                    executeProblemsCounter.incrementAndGet();
                    return ConnectionState.DISCONNECTED;
                }
            case CONNECTED:
                try {
                    if (isStale(currentConnection))
                        return refreshConnection(connectionState);
                    currentConnection.handshake();
                    successfulHandshakes.getAndIncrement();
                } catch (ServerResponseException ser) {
                    int code = ser.getResponseCode();
                    if (code == 401 || code == 403) {
                        drainDocumentQueueWhenFailingPermanently(new Exception("Denied access by endpoint:" + ser.getResponseString()));
                        log.log(Level.SEVERE, "Failed authentication or authorization with '" + endpoint + "': " + Exceptions.toMessageString(ser));
                        return ConnectionState.CONNECTED; // Should ideally exit immediately, instead of doing this per X documents :/
                    }

                    executeProblemsCounter.incrementAndGet();
                    log.log(Level.INFO, "Failed talking to endpoint. Handshake with server endpoint '" + endpoint +
                                        "' failed -- will re-try handshake: " + Exceptions.toMessageString(ser));

                    drainFirstDocumentsInQueueIfOld();
                    resultQueue.onEndpointError(new FeedProtocolException(ser.getResponseCode(), ser.getResponseString(), ser, endpoint));
                    return ConnectionState.CONNECTED;
                } catch (Throwable throwable) { // This cover IOException as well
                    executeProblemsCounter.incrementAndGet();
                    resultQueue.onEndpointError(new FeedConnectException(throwable, endpoint));
                    log.log(Level.INFO, "Failed talking to endpoint. Handshake with server endpoint '" +
                                        endpoint + "' failed. Will re-try handshake.",
                            throwable);
                    drainFirstDocumentsInQueueIfOld();
                    currentConnection.close();
                    return ConnectionState.DISCONNECTED;
                }
                return ConnectionState.SESSION_SYNCED;
            case SESSION_SYNCED:
                try {
                    if (isStale(currentConnection))
                        return refreshConnection(connectionState);
                    ProcessResponse processResponse = pullAndProcessData(pollIntervalUS);
                    gatewayThrottler.handleCall(processResponse.transitiveErrorCount);
                }
                catch (ServerResponseException ser) {
                    log.log(Level.INFO, "Problems while handing data over to endpoint '" + endpoint +
                                        "'. Will re-try. Endpoint responded with an unexpected HTTP response code.",
                            ser);
                    return ConnectionState.CONNECTED;
                }
                catch (Throwable e) {
                    log.log(Level.INFO,
                            "Connection level error handing data over to endpoint '" + endpoint + "'. Will re-try.",
                            e);
                    currentConnection.close();
                    return ConnectionState.DISCONNECTED;
                }
                return ConnectionState.SESSION_SYNCED;
            default: {
                log.severe("Should never get here.");
                currentConnection.close();
                return ConnectionState.DISCONNECTED;
            }
        }
    }

    private void sleepIfProblemsGettingSyncedConnection(ConnectionState newState, ConnectionState oldState) {
        if (newState == ConnectionState.SESSION_SYNCED) return;
        if (newState == ConnectionState.CONNECTED && oldState == ConnectionState.DISCONNECTED) return;
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
        while (stopSignal.getCount() > 0 || !documentQueue.isEmpty())
            tick();
        log.finer(toString() + " exiting, documentQueue.size()=" + documentQueue.size());
        running.countDown();
    }

    /** Do one iteration of work. Should be called from the single worker thread of this. */
    public void tick() {
        ConnectionState oldState = connectionState;
        connectionState = cycle(connectionState);
        if (thread == null)
            oldConnectionsDrainer.checkOldConnections();
        if (thread != null)
            sleepIfProblemsGettingSyncedConnection(connectionState, oldState);
    }

    private void drainFirstDocumentsInQueueIfOld() {
        while (true) {
            Optional<Document> document = documentQueue.pollDocumentIfTimedoutInQueue(localQueueTimeOut);
            if ( ! document.isPresent()) return;

            EndpointResult endpointResult = EndPointResultFactory.createTransientError(
                    endpoint, document.get().getOperationId(),
                    new Exception("Not sending document operation, timed out in queue after " +
                                  (clock.millis() - document.get().getQueueInsertTime().toEpochMilli()) + " ms."));
            resultQueue.failOperation(endpointResult, clusterId);
        }
    }

    private void drainDocumentQueueWhenFailingPermanently(Exception exception) {
        // first, clear sentOperations:
        resultQueue.failPending(exception);

        for (Document document : documentQueue.removeAllDocuments()) {
            EndpointResult endpointResult=
                    EndPointResultFactory.createError(endpoint, document.getOperationId(), exception);
            resultQueue.failOperation(endpointResult, clusterId);
        }
    }

    private boolean isStale(GatewayConnection connection) {
        return connection.connectionTime() != null
               && connection.connectionTime().plus(connectionTimeToLive).isBefore(clock.instant());
    }

    private ConnectionState refreshConnection(ConnectionState currentConnectionState) {
        if (currentConnectionState == ConnectionState.SESSION_SYNCED)
            oldConnectionsDrainer.add(currentConnection);
        currentConnection = connectionFactory.newConnection();
        return ConnectionState.DISCONNECTED;
    }

    public static class ConnectionStats {

        // NOTE: These fields are accessed by reflection in JSON serialization

        public final int wrongSessionDetectedCounter;
        public final int wrongVersionDetectedCounter;
        public final int problemStatusCodeFromServerCounter;
        public final int executeProblemsCounter;
        public final int docsReceivedCounter;
        public final int statusReceivedCounter;
        public final int pendingDocumentStatusCount;
        public final int successfullHandshakes;
        public final int lastGatewayProcessTimeMillis;

        ConnectionStats(int wrongSessionDetectedCounter,
                        int wrongVersionDetectedCounter,
                        int problemStatusCodeFromServerCounter,
                        int executeProblemsCounter,
                        int docsReceivedCounter,
                        int statusReceivedCounter,
                        int pendingDocumentStatusCount,
                        int successfullHandshakes,
                        int lastGatewayProcessTimeMillis) {
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

    /** For testing. Returns the current connection of this. Not thread safe. */
    public GatewayConnection currentConnection() { return currentConnection; }

    /** For testing. Returns a snapshot of the old connections of this. */
    public List<GatewayConnection> oldConnections() { return oldConnectionsDrainer.connections(); }

    /** For testing */
    public EndpointResultQueue resultQueue() { return resultQueue; }

    /**
     * We need to drain results on the connection where they were sent to make sure we request results on
     * the node which received the operation also when going through a VIP.
     */
    private static class OldConnectionsDrainer implements Runnable {

        private static final Logger log = Logger.getLogger(OldConnectionsDrainer.class.getName());

        private final Endpoint endpoint;
        private final int clusterId;
        private final Duration pollInterval;
        private final Duration connectionTimeToLive;
        private final Duration localQueueTimeOut;
        private final AtomicInteger statusReceivedCounter;
        private final EndpointResultQueue resultQueue;
        private final CountDownLatch stopSignal;
        private final Clock clock;

        /**
         * Previous connections on which we may have sent operations and are still waiting for the results
         * All connections in this are in state SESSION_SYNCED.
         */
        private final List<GatewayConnection> connections = new CopyOnWriteArrayList<>();

        OldConnectionsDrainer(Endpoint endpoint,
                             int clusterId,
                             Duration pollInterval,
                             Duration connectionTimeToLive,
                             Duration localQueueTimeOut,
                             AtomicInteger statusReceivedCounter,
                             EndpointResultQueue resultQueue,
                             CountDownLatch stopSignal,
                             Clock clock) {
            this.endpoint = endpoint;
            this.clusterId = clusterId;
            this.pollInterval = pollInterval;
            this.connectionTimeToLive = connectionTimeToLive;
            this.localQueueTimeOut = localQueueTimeOut;
            this.statusReceivedCounter = statusReceivedCounter;
            this.resultQueue = resultQueue;
            this.stopSignal = stopSignal;
            this.clock = clock;
        }

        /** Add another old connection to this for draining */
        public void add(GatewayConnection connection) {
            connections.add(connection);
        }

        @Override
        public void run() {
            while (stopSignal.getCount() > 0) {
                try {
                    checkOldConnections();
                    Thread.sleep(pollInterval.toMillis());
                }
                catch (InterruptedException e) {
                    log.log(Level.WARNING, "Close thread was interrupted: " + e.getMessage(), e);
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.log(Level.WARNING, "Connection draining failed: " + e.getMessage(), e);
                }
            }
        }

        public void checkOldConnections() {
            for (GatewayConnection connection : connections) {
                if (!resultQueue.hasInflightOperations(connection)) {
                    log.fine(() -> connection + " no longer has inflight operations");
                    closeConnection(connection);
                } else if (closingTime(connection).isBefore(clock.instant())) {
                    log.fine(() -> connection + " still has inflight operations, but drain period is over");
                    tryPollAndDrainInflightOperations(connection);
                    closeConnection(connection);
                } else if (timeToPoll(connection)) {
                    tryPollAndDrainInflightOperations(connection);
                }
            }
        }

        private void closeConnection(GatewayConnection connection) {
            log.fine(() -> "Closing " + connection);
            connection.close();
            connections.remove(connection); // Safe as CopyOnWriteArrayList allows removal during iteration
        }

        private void tryPollAndDrainInflightOperations(GatewayConnection connection) {
            try {
                log.fine(() -> "Polling and draining inflight operations for " + connection);
                IOThread.processResponse(connection.poll(), endpoint, clusterId, statusReceivedCounter, resultQueue);
            } catch (Exception e) {
                // Old connection; best effort
                log.log(Level.FINE, e, () -> "Polling status of inflight operations failed: " + e.getMessage());
            }
        }

        private boolean timeToPoll(GatewayConnection connection) {
            // connectionEndOfLife < connectionLastPolled < now
            Instant now = clock.instant();
            Instant endOfLife = connection.connectionTime().plus(connectionTimeToLive);
            if (connection.lastPollTime() == null) return endOfLife.plus(pollInterval).isBefore(now);
            if (connection.lastPollTime().plus(pollInterval).isAfter(now)) return false;

            // Exponential (2^x) dropoff:
            double connectionEndOfLife = endOfLife.toEpochMilli();
            double connectionLastPolled = connection.lastPollTime().toEpochMilli();
            return now.toEpochMilli() - connectionEndOfLife > 2 * (connectionLastPolled - connectionEndOfLife);
        }

        private Instant closingTime(GatewayConnection connection) {
            return connection.connectionTime().plus(connectionTimeToLive).plus(localQueueTimeOut);
        }

        private void close() {
            int size = resultQueue.getPendingSize();
            if (size > 0) {
                log.info("We have outstanding operations (" + size + ") , trying to fetch responses.");
                for (GatewayConnection connection : connections) {
                    try {
                        IOThread.processResponse(connection.poll(), endpoint, clusterId, statusReceivedCounter, resultQueue);
                    } catch (Throwable e) {
                        log.log(Level.SEVERE, "Some failures while trying to get latest responses from vespa.", e);
                    }
                }
            }
            for (GatewayConnection oldConnection : connections)
                oldConnection.close();
        }

        /** For testing. Returns the old connections of this. */
        public List<GatewayConnection> connections() { return Collections.unmodifiableList(connections); }

    }

}
