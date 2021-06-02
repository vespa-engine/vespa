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
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.logging.Level.INFO;

/**
 * Controls request execution and retries:
 * <ul>
 *     <li>Retry all IO exceptions; however</li>
 *     <li>abort everything if more than 10% of requests result in an exception for some time.</li>
 *     <li>Whenever throttled, limit inflight to one less than the current; and</li>
 *     <li>on every successful response, increase inflight limit by 0.1.</li>
 * </ul>
 *
 * @author jonmv
 */
class HttpRequestStrategy implements RequestStrategy {

    private static final Logger log = Logger.getLogger(HttpRequestStrategy.class.getName());

    private final Map<DocumentId, CompletableFuture<Void>> inflightById = new HashMap<>();
    private final Object lock = new Object();
    private final Clock clock;
    private final RetryStrategy wrapped;
    private final long maxInflight;
    private final long minInflight;
    private double targetInflight;
    private long inflight = 0;
    private long consecutiveSuccesses = 0;
    private boolean failed = false;
    private Instant lastSuccess;

    HttpRequestStrategy(FeedClientBuilder builder, Clock clock) {
        this.wrapped = builder.retryStrategy;
        this.maxInflight = builder.maxConnections * (long) builder.maxStreamsPerConnection;
        this.minInflight = builder.maxConnections * (long) min(16, builder.maxStreamsPerConnection);
        this.targetInflight = Math.sqrt(maxInflight) * (Math.sqrt(minInflight));
        this.clock = clock;
        lastSuccess = clock.instant();
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
        synchronized (lock) {
            ++consecutiveSuccesses;
            lastSuccess = now;
            targetInflight = min(targetInflight + 0.1, maxInflight);
        }
    }

    void failure() {
        Instant threshold = clock.instant().minusSeconds(300);
        synchronized (lock) {
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

        log.log(INFO, () -> "Status code " + response.getCode() + " (" + response.getBodyText() + ") on attempt " + attempt +
                            " at " + request + ", " + consecutiveSuccesses + " successes since last error");

        if (response.getCode() == 429 || response.getCode() == 503) { // Throttling; reduce target inflight.
            synchronized (lock) {
                targetInflight = max(inflight * 0.9, minInflight);
            }
            return true;
        }

        failure();
        if (response.getCode() != 500 && response.getCode() != 502)
            return false;

        return retry(request, attempt); // Hopefully temporary errors.
    }

    // Must hold lock.
    private void acquireSlot() {
        try {
            while (inflight >= targetInflight)
                lock.wait();

            ++inflight;
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Must hold lock.
    private void releaseSlot() {
        for (long i = --inflight; i < targetInflight; i++)
            lock.notify();
    }

    @Override
    public boolean hasFailed() {
        synchronized (lock) {
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
        synchronized (lock) {
            previous = inflightById.put(documentId, blocker);
            if (previous == null)
                acquireSlot();
        }
        if (previous == null)   // Send immediately if none inflight ...
            dispatch.accept(request, vessel);
        else                    // ... or send when the previous inflight is done.
            previous.thenRun(() -> dispatch.accept(request, vessel));

        handleAttempt(vessel, dispatch, blocker, request, result, documentId, 1);
        return result;
    }

    /** Handles the result of one attempt at the given operation, retrying if necessary. */
    private void handleAttempt(CompletableFuture<SimpleHttpResponse> vessel, BiConsumer<SimpleHttpRequest, CompletableFuture<SimpleHttpResponse>> dispatch,
                               CompletableFuture<Void> blocker, SimpleHttpRequest request, CompletableFuture<SimpleHttpResponse> result,
                               DocumentId documentId, int attempt) {
        vessel.whenComplete((response, thrown) -> {
            // Retry the operation if it failed with a transient error ...
            if ( ! failed && (thrown != null ? retry(request, thrown, attempt)
                                             : retry(request, response, attempt))) {
                    CompletableFuture<SimpleHttpResponse> retry = new CompletableFuture<>();
                    dispatch.accept(request, retry);
                    handleAttempt(retry, dispatch, blocker, request, result, documentId, attempt + 1);
                    return;
                }

            // ... or accept the outcome and mark the operation as complete.
            CompletableFuture<Void> current;
            synchronized (lock) {
                current = inflightById.get(documentId);
                if (current == blocker) {   // Release slot and clear map if no other operations enqueued for this doc-id ...
                    releaseSlot();
                    inflightById.put(documentId, null);
                }
            }
            if (current != blocker)         // ... or trigger sending the next enqueued operation.
                blocker.complete(null);

            if (thrown == null) result.complete(response);
            else result.completeExceptionally(thrown);
        });
    }

}
