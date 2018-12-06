package com.yahoo.vespa.http.client;

import com.google.common.annotations.Beta;
import com.yahoo.vespa.http.client.config.SessionParams;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A utility wrapper of a FeedClient which feeds a list of documents and blocks until all responses are returned,
 * before returning the results.
 *
 * Not multithread safe: A sync feed client instance can only be used by a single thread
 * (but it can and should be reused for multiple subsequent synchronous calls).
 *
 * @author bratseth
 */
@Beta
public class SyncFeedClient implements AutoCloseable {

    private final FeedClient wrappedClient;
    private final Callback callback;

    public SyncFeedClient(SessionParams sessionParams) {
        callback = new SyncFeedClient.Callback();
        this.wrappedClient = FeedClientFactory.create(sessionParams, callback);
    }

    /**
     * Calls FeedClient.stream for each entry in the list, blocks until all results are ready and returns them.
     * This will block for at most the time it takes to feed these operations + clientTimeout given in the
     * sessions params when creating this.
     *
     * @param operations the Vespa write operations to stream
     * @return the result of feeding all these operations
     */
    public SyncResult stream(List<SyncOperation> operations) {
        callback.expectResultsOf(operations);
        for (SyncOperation operation : operations)
            wrappedClient.stream(operation.documentId, operation.documentData, operation.context);
        return callback.waitForResults();
    }

    @Override
    public void close() {
        wrappedClient.close();
    }

    /** Holds the arguments to a single stream operation */
    public static class SyncOperation {

        private final String documentId;
        private final CharSequence documentData;
        private final Object context;

        public SyncOperation(String documentId, CharSequence documentData) {
            this(documentId, documentData, null);
        }

        public SyncOperation(String documentId, CharSequence documentData, Object context) {
            this.documentId = Objects.requireNonNull(documentId, "documentId");
            this.documentData = Objects.requireNonNull(documentData, "documentData");
            this.context = context;
        }

    }

    /**
     * The result of a SyncFeedClient.stream call. This always holds exactly one Result per SyncOperation
     * attempted, and the results are guaranteed to be returned in the same order as in the List of SyncOperations.
     */
    public static class SyncResult {

        private final Exception exception;
        private final List<Result> results;

        private SyncResult(List<Result> results, Exception exception) {
            this.results = results;
            this.exception = exception;
        }

        /**
         * Returns the results of this. This has the same size and order as the List of SyncOperations that
         * created this. The list returned is modifiable and owned by the client. Multiple calls to this returns the
         * same list instance.
         */
        public List<Result> results() { return results; }

        /**
         * Returns the last exception received when attempting the operations this is the result of, or null if none.
         * Even if there is an exception, results() will return one Result per operation attempted.
         */
        public Exception exception() { return exception; }

        /** Returns true if all Results in this are successful */
        public boolean isSuccess() {
            return results.stream().allMatch(Result::isSuccess);
        }

    }

    private static class Callback implements FeedClient.ResultCallback {

        private final Object monitor = new Object();

        // The rest of the state of this is reset each time we call expectResultsOf

        private int resultsReceived;
        private Exception exception = null;

        /**
         * A map from document ids to their results. This is initially populated with null values to keep track of
         * which responses we are waiting for.
         */
        private LinkedHashMap<String, Result> results = null;

        void expectResultsOf(List<SyncOperation> operations) {
            synchronized (monitor) {
                if (results != null)
                    throw new ConcurrentModificationException("A SyncFeedClient instance is used by multiple threads");

                resultsReceived = 0;
                exception = null;
                results = new LinkedHashMap<>(operations.size());
                for (SyncOperation operation : operations)
                    results.put(operation.documentId, null);
            }
        }

        SyncResult waitForResults() {
            try {
                synchronized (monitor) {
                    while ( ! complete())
                        monitor.wait();

                    SyncResult syncResult = new SyncResult(new ArrayList<>(results.values()), exception);
                    results = null;
                    return syncResult;
                }
            }
            catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for feeding results", e);
            }
        }

        @Override
        public void onCompletion(String docId, Result documentResult) {
            synchronized (monitor) {
                if ( ! results.containsKey(docId)) return; // Not expecting this result - ignore

                Result previousValue = results.put(docId, documentResult);
                if (previousValue != null)
                    throw new IllegalStateException("Received duplicate result for " + docId);

                resultsReceived++;
                if (complete())
                    monitor.notifyAll();
            }
        }

        @Override
        public void onEndpointException(FeedEndpointException exception) {
            this.exception = exception; // We will still receive one onCompletion per stream invocation done
        }

        private boolean complete() {
            return resultsReceived == results.size();
        }

    }

}
