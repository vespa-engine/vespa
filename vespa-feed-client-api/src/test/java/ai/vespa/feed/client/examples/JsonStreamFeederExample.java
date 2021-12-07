// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.examples;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.OperationParameters;
import ai.vespa.feed.client.Result;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple Streaming feeder implementation which will send operations to a Vespa endpoint.
 * Other threads communicate with the feeder by adding new operations on the BlockingQueue
 */

class JsonStreamFeederExample extends Thread implements AutoCloseable {

    static class Operation {
        final String type;
        final String documentId;
        final String documentFieldsJson;

        Operation(String type, String id, String fields) {
            this.type = type;
            this.documentId = id;
            this.documentFieldsJson = fields;
        }
    }

    private final static Logger log = Logger.getLogger(JsonStreamFeederExample.class.getName());

    private final BlockingQueue<Operation> operations;
    private final FeedClient feedClient;
    private final AtomicBoolean  drain = new AtomicBoolean(false);
    private final CountDownLatch finishedDraining = new CountDownLatch(1);
    private final AtomicInteger resultCounter = new AtomicInteger();

    /**
     * Constructor
     * @param operations The shared blocking queue where other threads can put document operations to.
     * @param endpoint The endpoint to feed to
     */
    JsonStreamFeederExample(BlockingQueue<JsonStreamFeederExample.Operation> operations, URI endpoint) {
        this.operations = operations;
        this.feedClient = FeedClientBuilder.create(endpoint).build();
    }

    /**
     * Shutdown this feeder, waits until operations on queue is drained
     */
    @Override
    public void close() {
        log.info("Shutdown initiated, awaiting operations queue to be drained. Queue size is " + operations.size());
        drain.set(true);
        try {
            finishedDraining.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        while (!drain.get() || !operations.isEmpty()) {
            try {
                JsonStreamFeederExample.Operation op = operations.poll(1, TimeUnit.SECONDS);
                if(op == null) // no operations available
                    continue;
                log.info("Put document " + op.documentId);
                CompletableFuture<Result> promise;
                DocumentId docId = DocumentId.of(op.documentId);
                OperationParameters params = OperationParameters.empty();
                String json = op.documentFieldsJson;
                switch (op.type) {
                    case "put":
                        promise = feedClient.put(docId, json, params);
                        break;
                    case "remove":
                        promise = feedClient.remove(docId, params);
                        break;
                    case "update":
                        promise = feedClient.update(docId, json, params);
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid operation: " + op.type);
                }
                promise.whenComplete((result, throwable) -> {
                    if (resultCounter.getAndIncrement() % 10 == 0) {
                        System.err.println(feedClient.stats());
                    }
                    if (throwable != null) {
                        System.err.printf("Failure for '%s': %s", docId, throwable);
                        throwable.printStackTrace();
                    }
                });
            } catch (InterruptedException e) {
                log.log(Level.SEVERE, "Got interrupt exception.", e);
                break;
            }
        }
        log.info("Shutting down feeding thread");
        this.feedClient.close();
        finishedDraining.countDown();
    }

}