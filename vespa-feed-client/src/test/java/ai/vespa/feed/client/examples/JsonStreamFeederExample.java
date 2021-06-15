// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.examples;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClientBuilder;
import ai.vespa.feed.client.OperationParameters;
import ai.vespa.feed.client.Result;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
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
    private final Object monitor = new Object();
    private final AtomicInteger sentCounter = new AtomicInteger();
    private final AtomicInteger resultCounter = new AtomicInteger();
    private final AtomicInteger failureCounter = new AtomicInteger();
    private int startSampleResultCount = 0;
    private Instant startSampleInstant = Instant.now();

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
                sentCounter.incrementAndGet();
                promise.whenComplete((result, throwable) -> {
                    if (resultCounter.getAndIncrement() % 10 == 0) {
                        printProgress();
                    }
                    if (throwable != null) {
                        failureCounter.incrementAndGet();
                        System.err.println("Failure: " + throwable);
                        throwable.printStackTrace();
                    } else if (result.type() == Result.Type.failure) {
                        failureCounter.incrementAndGet();
                        System.err.println("Failure: " + result.resultMessage());
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

    void printProgress() {
        synchronized (monitor) {
            Instant now = Instant.now();
            int resultCounter = this.resultCounter.get();
            int failureCounter = this.failureCounter.get();
            int sentCounter = this.sentCounter.get();
            double docsDelta = resultCounter - failureCounter - startSampleResultCount;
            Duration duration = Duration.between(startSampleInstant, now);
            startSampleInstant = now;
            this.startSampleResultCount = resultCounter - failureCounter;
            long durationMilliSecs = duration.toMillis() + 1; // +1 to avoid division by zero
            double rate = 1000. * docsDelta / durationMilliSecs;
            System.err.println(new Date() + " Result received: " + resultCounter
                    + " (" + failureCounter + " failed so far, " + sentCounter
                    + " sent, success rate " + String.format(Locale.US, "%.2f docs/sec", rate) + ").");
        }
    }
}