// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.examples;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.FeedException;
import ai.vespa.feed.client.JsonFeeder;
import ai.vespa.feed.client.Result;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Sample feeder demonstrating how to programmatically feed to a Vespa cluster.
 */
class JsonFileFeederExample implements Closeable {

    private final static Logger log = Logger.getLogger(JsonFileFeederExample.class.getName());

    private final JsonFeeder jsonFeeder;
    private final URI endpoint;

    static class ResultCallBack implements JsonFeeder.ResultCallback {

        final AtomicInteger resultsReceived = new AtomicInteger(0);
        final AtomicInteger errorsReceived = new AtomicInteger(0);
        final long startTimeMillis = System.currentTimeMillis();;

        @Override
        public void onNextResult(Result result, FeedException error) {
            resultsReceived.incrementAndGet();
            if (error != null) {
                log.warning("Problems with feeding document "
                        + error.documentId().map(DocumentId::toString).orElse("<unknown>")
                        + ": " + error);
                errorsReceived.incrementAndGet();
            }
        }

        @Override
        public void onError(FeedException error) {
            log.severe("Feeding failed fatally: " + error.getMessage());
        }

        @Override
        public void onComplete() {
            log.info("Feeding completed");
        }

        void dumpStatsToLog() {
            log.info("Received in total " + resultsReceived.get() + ", " + errorsReceived.get() + " errors.");
            log.info("Time spent receiving is " + (System.currentTimeMillis() - startTimeMillis) + " ms.");
        }

    }

    JsonFileFeederExample(URI endpoint) {
        this.endpoint = endpoint;
        FeedClient feedClient = FeedClientBuilder.create(endpoint)
                .build();
        this.jsonFeeder = JsonFeeder.builder(feedClient)
                .withTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Feed all operations from a stream.
     *
     * @param stream The input stream to read operations from (JSON array containing one or more document operations).
     */
    void batchFeed(InputStream stream, String batchId) {
        ResultCallBack callback = new ResultCallBack();
        log.info("Starting feed to " + endpoint + " for batch '" + batchId + "'");
        CompletableFuture<Void> promise = jsonFeeder.feedMany(stream, callback);
        promise.join(); // wait for feeding to complete
        callback.dumpStatsToLog();
    }

    @Override
    public void close() throws IOException {
        jsonFeeder.close();
    }
}
