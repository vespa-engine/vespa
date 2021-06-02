// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import ai.vespa.feed.client.FeedClient.RetryStrategy;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

/**
 * Controls request execution and retries:
 * <ul>
 *     <li>Whenever throttled (429, 503), set target inflight to 0.9 * current, and retry over a different connection;</li>
 *     <li>retry other transient errors (500, 502 and IOException) a specified number of times, for specified operation types;</li>
 *     <li>and on every successful response, increase target inflight by 0.1.</li>
 * </ul>
 *
 * @author jonmv
 */
class HttpRequestStrategy implements RequestStrategy, AutoCloseable {

    private static final Logger log = Logger.getLogger(HttpRequestStrategy.class.getName());

    private final Map<DocumentId, CompletableFuture<Void>> inflightById = new HashMap<>();
    private final Object monitor = new Object();
    private final Clock clock;
    private final RetryStrategy wrapped;
    private final Thread delayer = new Thread(this::drainDelayed);
    private final BlockingQueue<CompletableFuture<Void>> delayed = new LinkedBlockingQueue<>();
    private final long maxInflight;
    private final long minInflight;
    private double targetInflight;
    private long inflight = 0;
    private long consecutiveSuccesses = 0;
    private Instant lastSuccess;
    private boolean failed = false;
    private boolean closed = false;

    HttpRequestStrategy(FeedClientBuilder builder, Clock clock) {
        this.wrapped = builder.retryStrategy;
        this.maxInflight = builder.maxConnections * (long) builder.maxStreamsPerConnection;
        this.minInflight = builder.maxConnections * (long) min(16, builder.maxStreamsPerConnection);
        this.targetInflight = Math.sqrt(maxInflight) * (Math.sqrt(minInflight));
        this.clock = clock;
        this.lastSuccess = clock.instant();
        this.delayer.start();
    }

    private void drainDelayed() {
        try {
            while (true) {
                do delayed.take().complete(null);
                while ( ! hasFailed());

                Thread.sleep(1000);
            }
        }
        catch (InterruptedException e) {
            delayed.forEach(action -> action.cancel(true));
        }
    }

    private boolean retry(SimpleHttpRequest request, int attempt) {
        if (attempt >= wrapped.retries())
            return false;

        switch (request.getMethod().toUpperCase()) {
            case "POST":   return wrapped.retry(FeedClient.OperationType.put);
            case "PUT":    return wrapped.retry(FeedClient.OperationType.update);
            case "DELETE": return wrapped.retry(FeedClient.OperationType.remove);
            default: throw new IllegalStateException("Unexpected HTTP method: " + request.getMethod());
        }
    }

    /**
     * Retries all IOExceptions, unless error rate has converged to a value higher than the threshold,
     * or the user has turned off retries for this type of operation.
     */
    private boolean retry(SimpleHttpRequest request, Throwable thrown, int attempt) {
        failure();
        log.log(INFO, thrown, () -> "Failed attempt " + attempt + " at " + request + ", " + consecutiveSuccesses + " successes since last error");

        if ( ! (thrown instanceof IOException))
            return false;

        return retry(request, attempt);
    }

    void success() {
        Instant now = clock.instant();
        synchronized (monitor) {
            ++consecutiveSuccesses;
            lastSuccess = now;
            targetInflight = min(targetInflight + 0.1, maxInflight);
        }
    }

    void failure() {
        Instant threshold = clock.instant().minusSeconds(300);
        synchronized (monitor) {
            consecutiveSuccesses = 0;
            if (lastSuccess.isBefore(threshold))
                failed = true;
        }
    }

    /** Retries throttled requests (429, 503), adjusting the target inflight count, and server errors (500, 502). */
    private boolean retry(SimpleHttpRequest request, SimpleHttpResponse response, int attempt) {
        if (response.getCode() / 100 == 2) {
            success();
            return false;
        }

        if (response.getCode() == 429 || response.getCode() == 503) { // Throttling; reduce target inflight.
            synchronized (monitor) {
                targetInflight = max(inflight * 0.9, minInflight);
            }
            log.log(FINE, () -> "Status code " + response.getCode() + " (" + response.getBodyText() + ") on attempt " + attempt +
                                " at " + request + ", " + consecutiveSuccesses + " successes since last error");

            return true;
        }

        log.log(INFO, () -> "Status code " + response.getCode() + " (" + response.getBodyText() + ") on attempt " + attempt +
                            " at " + request + ", " + consecutiveSuccesses + " successes since last error");

        failure();
        if (response.getCode() != 500 && response.getCode() != 502)
            return false;

        return retry(request, attempt); // Hopefully temporary errors.
    }

    // Must hold lock.
    private void acquireSlot() {
        try {
            while (inflight >= targetInflight)
                monitor.wait();

            ++inflight;
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Must hold lock.
    private void releaseSlot() {
        for (long i = --inflight; i < targetInflight; i++)
            monitor.notify();
    }

    @Override
    public boolean hasFailed() {
        synchronized (monitor) {
            return failed;
        }
    }

    @Override
    public CompletableFuture<SimpleHttpResponse> enqueue(DocumentId documentId, SimpleHttpRequest request,
                                                         BiConsumer<SimpleHttpRequest, CompletableFuture<SimpleHttpResponse>> dispatch) {
        CompletableFuture<SimpleHttpResponse> result = new CompletableFuture<>(); // Carries the aggregate result of the operation, including retries.
        CompletableFuture<SimpleHttpResponse> vessel = new CompletableFuture<>(); // Holds the computation of a single dispatch to the HTTP client.
        CompletableFuture<Void> blocker = new CompletableFuture<>();              // Blocks the next operation with same doc-id, then triggers it when complete.

        // Get the previous inflight operation for this doc-id, or acquire a send slot.
        CompletableFuture<Void> previous;
        synchronized (monitor) {
            previous = inflightById.put(documentId, blocker);
            if (previous == null)
                acquireSlot();
        }
        if (previous == null)   // Send immediately if none inflight ...
            dispatch.accept(request, vessel);
        else                    // ... or send when the previous inflight is done.
            previous.thenRun(() -> dispatch.accept(request, vessel));

        handleAttempt(vessel, dispatch, request, result, 1);

        result.thenRun(() -> {
            CompletableFuture<Void> current;
            synchronized (monitor) {
                current = inflightById.get(documentId);
                if (current == blocker) {   // Release slot and clear map if no other operations enqueued for this doc-id ...
                    releaseSlot();
                    inflightById.put(documentId, null);
                }
            }
            if (current != blocker)         // ... or trigger sending the next enqueued operation.
                blocker.complete(null);
        });

        return result;
    }

    /** Handles the result of one attempt at the given operation, retrying if necessary. */
    private void handleAttempt(CompletableFuture<SimpleHttpResponse> vessel, BiConsumer<SimpleHttpRequest, CompletableFuture<SimpleHttpResponse>> dispatch,
                               SimpleHttpRequest request, CompletableFuture<SimpleHttpResponse> result, int attempt) {
        vessel.whenComplete((response, thrown) -> {
            // Retry the operation if it failed with a transient error ...
            if (thrown != null ? retry(request, thrown, attempt)
                               : retry(request, response, attempt)) {
                CompletableFuture<SimpleHttpResponse> retry = new CompletableFuture<>();
                boolean hasFailed = hasFailed();
                if (hasFailed)
                    delayed.add(new CompletableFuture<>().thenRun(() -> dispatch.accept(request, retry)));
                else
                    dispatch.accept(request, retry);
                handleAttempt(retry, dispatch, request, result, attempt + (hasFailed ? 0 : 1));
                return;
            }

            // ... or accept the outcome and mark the operation as complete.
            if (thrown == null) result.complete(response);
            else result.completeExceptionally(thrown);
        });
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed)
                return;

            closed = true;
        }
        delayer.interrupt();
        try { delayer.join(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

}
