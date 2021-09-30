// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import ai.vespa.feed.client.FeedClient.CircuitBreaker.State;

import java.util.concurrent.CompletableFuture;

/**
 * Controls execution of feed operations.
 *
 * @author jonmv
 */
interface RequestStrategy {

    /** Stats for operations sent through this. */
    OperationStats stats();

    /** State of the circuit breaker. */
    State circuitBreakerState();

    /** Forcibly terminates this, causing all inflight operations to complete immediately. */
    void destroy();

    /** Wait for all inflight requests to complete. */
    void await();

    /** Enqueue the given operation, returning its future result. This may block if the client send queue is full. */
    CompletableFuture<HttpResponse> enqueue(DocumentId documentId, HttpRequest request);

}
