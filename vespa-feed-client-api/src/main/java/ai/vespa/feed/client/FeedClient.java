// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous feed client accepting document operations as JSON. The payload should be
 * the same as the HTTP payload required by the /document/v1 HTTP API, i.e., <pre>
 *     {
 *         "fields": {
 *             ...
 *         }
 *     }
 * </pre>
 *
 * @author bjorncs
 * @author jonmv
 */
public interface FeedClient extends Closeable {

    /**
     * Send a document put with the given parameters, returning a future with the result of the operation.
     * Exceptional completion will use be an instance of {@link FeedException} or one of its sub-classes.
     */
    CompletableFuture<Result> put(DocumentId documentId, String documentJson, OperationParameters params);

    /**
     * Send a document update with the given parameters, returning a future with the result of the operation.
     * Exceptional completion will use be an instance of {@link FeedException} or one of its sub-classes.
     */
    CompletableFuture<Result> update(DocumentId documentId, String updateJson, OperationParameters params);

    /**
     * Send a document remove with the given parameters, returning a future with the result of the operation.
     * Exceptional completion will use be an instance of {@link FeedException} or one of its sub-classes.
     */
    CompletableFuture<Result> remove(DocumentId documentId, OperationParameters params);

    /**
     * Waits for all feed operations to complete, either successfully or with exception.
     * @throws MultiFeedException if any operation fails
     * @return list of results with the same ordering as the {@code promises} parameter
     * */
    static List<Result> await(List<CompletableFuture<Result>> promises) throws MultiFeedException {
        return Helper.await(promises);
    }

    /**
     * Same as {@link #await(List)} except {@code promises} parameter is a vararg
     * @see #await(List)
     */
    @SafeVarargs
    static List<Result> await(CompletableFuture<Result>... promises) throws MultiFeedException {
        return Helper.await(promises);
    }

    /** Returns a snapshot of the stats for this feed client, such as requests made, and responses by status. */
    OperationStats stats();

    /** Current state of the circuit breaker. */
    CircuitBreaker.State circuitBreakerState();

    /** Shut down, and reject new operations. Operations in flight are allowed to complete normally if graceful. */
    void close(boolean graceful);

    /** Initiates graceful shutdown. See {@link #close(boolean)}. */
    default void close() { close(true); }

    /** Controls what to retry, and how many times. */
    interface RetryStrategy {

        /** Whether to retry operations of the given type. */
        default boolean retry(OperationType type) { return true; }

        /** Number of retries per operation for assumed transient, non-backpressure problems. */
        default int retries() { return 10; }

    }

    /** Allows slowing down or halting completely operations against the configured endpoint on high failure rates. */
    interface CircuitBreaker {

        /** A circuit breaker which is always closed. */
        CircuitBreaker FUSED = () -> State.CLOSED;

        /** Called by the client whenever a successful response is obtained. */
        default void success() { }

        /** Called by the client whenever an error HTTP response is received. */
        default void failure(HttpResponse response) { }

        /** Called by the client whenever an exception occurs trying to obtain a HTTP response. */
        default void failure(Throwable cause) { }

        /** The current state of the circuit breaker. */
        State state();

        enum State {

            /** Circuit is closed: business as usual. */
            CLOSED,

            /** Circuit is half-open: something is wrong, perhaps it recovers? */
            HALF_OPEN,

            /** Circuit is open: we have given up. */
            OPEN;

        }

    }

    enum OperationType {

        /** A document put operation. This is idempotent. */
        PUT,

        /** A document update operation. This is idempotent if all its contained updates are. */
        UPDATE,

        /** A document remove operation. This is idempotent. */
        REMOVE;

    }


}
