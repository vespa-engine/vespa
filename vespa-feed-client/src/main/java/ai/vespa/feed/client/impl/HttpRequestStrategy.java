// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClient.CircuitBreaker;
import ai.vespa.feed.client.FeedClient.RetryStrategy;
import ai.vespa.feed.client.FeedException;
import ai.vespa.feed.client.HttpResponse ;
import ai.vespa.feed.client.OperationStats;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ai.vespa.feed.client.FeedClient.CircuitBreaker.State.CLOSED;
import static ai.vespa.feed.client.FeedClient.CircuitBreaker.State.HALF_OPEN;
import static ai.vespa.feed.client.FeedClient.CircuitBreaker.State.OPEN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;

/**
 * Controls request execution and retries.
 *
 * This class has all control flow for throttling and dispatching HTTP requests to an injected
 * HTTP {@link Cluster}, including error handling and retries through a {@link RetryStrategy},
 * a {@link CircuitBreaker} mechanism, and a {@link Throttler} for optimal load.
 *
 * Dispatch to the provided {@link Cluster} is done by a single dispatch thread. If dispatch ever throws,
 * or the circuit breaker ever opens completely, the dispatch thread stops and all execution shuts down.
 * This is done through {@link #destroy()}, which when called also ensures all enqueued operations are
 * promptly completed, in addition to releasing any resources (threads, and in the provided cluster}.
 *
 * @author jonmv
 */
class HttpRequestStrategy implements RequestStrategy {

    private static final Logger log = Logger.getLogger(HttpRequestStrategy.class.getName());

    private final Cluster cluster;
    private final Map<DocumentId, RetriableFuture<HttpResponse>> inflightById = new ConcurrentHashMap<>();
    private final RetryStrategy strategy;
    private final CircuitBreaker breaker;
    private final Throttler throttler;
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong inflight = new AtomicLong(0);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final AtomicLong delayedCount = new AtomicLong(0);
    private final ExecutorService resultExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "feed-client-result-executor");
        thread.setDaemon(true);
        return thread;
    });

    HttpRequestStrategy(FeedClientBuilderImpl builder) throws IOException {
        this(builder, builder.dryrun ? new DryrunCluster() : new ApacheCluster(builder));
    }

    HttpRequestStrategy(FeedClientBuilderImpl builder, Cluster cluster) {
        this.cluster = builder.benchmark ? new BenchmarkingCluster(cluster) : cluster;
        this.strategy = builder.retryStrategy;
        this.breaker = builder.circuitBreaker;
        this.throttler = new DynamicThrottler(builder);

        Thread dispatcher = new Thread(this::dispatch, "feed-client-dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    @Override
    public OperationStats stats() {
        return cluster.stats();
    }

    @Override
    public CircuitBreaker.State circuitBreakerState() {
        return breaker.state();
    }

    private void dispatch() {
        try {
            while (breaker.state() != OPEN && ! destroyed.get()) {
                while ( ! isInExcess() && poll() && breaker.state() == CLOSED);
                // Sleep when circuit is half-open, nap when queue is empty, or we are throttled.
                Thread.sleep(breaker.state() == HALF_OPEN ? 1000 : 10);
            }
        }
        catch (Throwable t) {
            log.log(WARNING, "Dispatch thread threw; shutting down", t);
        }
        destroy();
    }

    private void offer(HttpRequest request, CompletableFuture<HttpResponse> vessel) {
        delayedCount.incrementAndGet();
        queue.offer(() -> cluster.dispatch(request, vessel));
    }

    private boolean poll() {
        Runnable task = queue.poll();
        if (task == null) return false;
        delayedCount.decrementAndGet();
        task.run();
        return true;
    }

    private boolean isInExcess() {
        return inflight.get() - delayedCount.get() > throttler.targetInflight();
    }

    private boolean retry(HttpRequest request, int attempt) {
        if (attempt > strategy.retries())
            return false;

        switch (request.method().toUpperCase()) {
            case "POST":   return strategy.retry(FeedClient.OperationType.PUT);
            case "PUT":    return strategy.retry(FeedClient.OperationType.UPDATE);
            case "DELETE": return strategy.retry(FeedClient.OperationType.REMOVE);
            default: throw new IllegalStateException("Unexpected HTTP method: " + request.method());
        }
    }

    /**
     * Retries all IOExceptions, unless error rate has converged to a value higher than the threshold,
     * or the user has turned off retries for this type of operation.
     */
    private boolean retry(HttpRequest request, Throwable thrown, int attempt) {
        breaker.failure(thrown);
        if (   (thrown instanceof IOException)               // General IO problems.
            || (thrown instanceof CancellationException)     // TLS session disconnect.
            || (thrown instanceof CancelledKeyException)) {  // Selection cancelled.
            log.log(FINER, thrown, () -> "Failed attempt " + attempt + " at " + request);
            return retry(request, attempt);
        }

        log.log(FINE, thrown, () -> "Failed attempt " + attempt + " at " + request);
        return false;
    }

    /** Retries throttled requests (429, 503), adjusting the target inflight count, and server errors (500, 502). */
    private boolean retry(HttpRequest request, HttpResponse response, int attempt) {
        if (response.code() / 100 == 2 || response.code() == 404 || response.code() == 412) {
            logResponse(FINEST, response, request, attempt);
            breaker.success();
            throttler.success();
            return false;
        }


        if (response.code() == 429 || response.code() == 503) { // Throttling; reduce target inflight.
            logResponse(FINER, response, request, attempt);
            throttler.throttled((inflight.get() - delayedCount.get()));
            return true;
        }

        logResponse(FINE, response, request, attempt);
        if (response.code() == 500 || response.code() == 502 || response.code() == 504) { // Hopefully temporary errors.
            breaker.failure(response);
            return retry(request, attempt);
        }

        return false;
    }

    static void logResponse(Level level, HttpResponse response, HttpRequest request, int attempt) {
        if (log.isLoggable(level))
            log.log(level, "Status code " + response.code() +
                           " (" + (response.body() == null ? "no body" : new String(response.body(), UTF_8)) +
                           ") on attempt " + attempt + " at " + request);
    }

    private void acquireSlot() {
        try {
            while (inflight.get() >= throttler.targetInflight())
                Thread.sleep(1);

            inflight.incrementAndGet();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void releaseSlot() {
        inflight.decrementAndGet();
    }

    public void await() {
        try {
            while (inflight.get() > 0)
                Thread.sleep(10);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    /** A completable future which stores a temporary failure result to return upon abortion. */
    private static class RetriableFuture<T> extends CompletableFuture<T> {

        private final AtomicReference<Runnable> completion = new AtomicReference<>();
        private final AtomicReference<RetriableFuture<T>> dependency = new AtomicReference<>();

        private RetriableFuture() {
            completion.set(() -> completeExceptionally(new FeedException("Operation aborted")));
        }

        /** Complete now with the last result or error. */
        void complete() {
            completion.get().run();
            RetriableFuture<T> toComplete = dependency.getAndSet(null);
            if (toComplete != null) toComplete.complete();
        }

        /** Ensures the dependency is completed whenever this is. */
        void dependOn(RetriableFuture<T> dependency) {
            this.dependency.set(dependency);
            if (isDone()) dependency.complete();
        }

        /** Set the result of the last attempt at completing the computation represented by this. */
        void set(T result, Throwable thrown) {
            completion.set(thrown != null ? () -> completeExceptionally(thrown)
                                          : () -> complete(result));
        }

    }
    @Override
    public CompletableFuture<HttpResponse> enqueue(DocumentId documentId, HttpRequest request) {
        RetriableFuture<HttpResponse> result = new RetriableFuture<>(); // Carries the aggregate result of the operation, including retries.
        if (destroyed.get()) {
            result.complete();
            return result;
        }

        CompletableFuture<HttpResponse> vessel = new CompletableFuture<>(); // Holds the computation of a single dispatch to the HTTP client.
        RetriableFuture<HttpResponse> previous = inflightById.put(documentId, result);
        if (previous == null) {
            acquireSlot();
            offer(request, vessel);
            throttler.sent(inflight.get(), result);
        }
        else {
            result.dependOn(previous); // In case result is aborted, also abort the previous if still inflight.
            previous.whenComplete((__, ___) -> offer(request, vessel));
        }

        handleAttempt(vessel, request, result, 1);

        return result.handle((response, error) -> {
            if (inflightById.compute(documentId, (__, current) -> current == result ? null : current) == null)
                releaseSlot();

            if (error != null) {
                if (error instanceof FeedException) throw (FeedException) error;
                throw new FeedException(documentId, error);
            }
            return response;
        });
    }

    /** Handles the result of one attempt at the given operation, retrying if necessary. */
    private void handleAttempt(CompletableFuture<HttpResponse> vessel, HttpRequest request, RetriableFuture<HttpResponse> result, int attempt) {
        vessel.whenCompleteAsync((response, thrown) -> {
                                     result.set(response, thrown);
                                     // Retry the operation if it failed with a transient error ...
                                     if (thrown != null ? retry(request, thrown, attempt)
                                                        : retry(request, response, attempt)) {
                                         CompletableFuture<HttpResponse> retry = new CompletableFuture<>();
                                         offer(request, retry);
                                         handleAttempt(retry, request, result, attempt + (breaker.state() == HALF_OPEN ? 0 : 1));
                                     }
                                     // ... or accept the outcome and mark the operation as complete.
                                     else result.complete();
                                 },
                                 resultExecutor);
    }

    @Override
    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            inflightById.values().forEach(RetriableFuture::complete);
            cluster.close();
            resultExecutor.shutdown();
            try {
                if ( ! resultExecutor.awaitTermination(1, TimeUnit.MINUTES))
                    log.log(WARNING, "Failed processing results within 1 minute");
            }
            catch (InterruptedException e) {
                log.log(WARNING, "Interrupted waiting for results to be processed");
                Thread.currentThread().interrupt();
            }
        }
    }

}
