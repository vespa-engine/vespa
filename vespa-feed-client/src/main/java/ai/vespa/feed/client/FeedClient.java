// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * @author bjorncs
 * @author jonmv
 */
public interface FeedClient extends Closeable {

    /** Send a document put with the given parameters, returning a future with the result of the operation. */
    CompletableFuture<Result> put(DocumentId documentId, String documentJson, OperationParameters params);

    /** Send a document update with the given parameters, returning a future with the result of the operation. */
    CompletableFuture<Result> update(DocumentId documentId, String updateJson, OperationParameters params);

    /** Send a document remove with the given parameters, returning a future with the result of the operation. */
    CompletableFuture<Result> remove(DocumentId documentId, OperationParameters params);

    /** Shut down, and reject new operations. Operations in flight are allowed to complete normally if graceful. */
    void close(boolean graceful);

    /** Initiates graceful shutdown. See {@link #close(boolean)}. */
    default void close() { close(true); }

    /** Controls what to retry, and how many times. */
    interface RetryStrategy {

        /** Whether to retry operations of the given type. */
        default boolean retry(OperationType type) { return true; }

        /** Number of retries per operation for non-backpressure problems. */
        default int retries() { return 32; }

    }

    /** Allows slowing down or halting completely operations against the configured endpoint on high failure rates. */
    interface CircuitBreaker {

        /** Called by the client whenever a successful response is obtained. */
        void success();

        /** Called by the client whenever a transient or fatal error occurs. */
        void failure();

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
