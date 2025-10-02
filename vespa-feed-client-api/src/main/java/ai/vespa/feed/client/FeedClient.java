// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.io.Closeable;
import java.time.Duration;
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

    /** Reset statistics. Useful for filtering out warmup operations. */
    void resetStats();

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

        /** Maximum number of retries per operation for assumed transient, non-backpressure problems. */
        default int retries() { return Integer.MAX_VALUE; }

    }

    /**
     * Allows slowing down—or halting—operations against the configured endpoint when failures persist.
     *
     * <p>The {@link FeedClient} calls {@link #success()}, {@link #failure(HttpResponse)}, and
     * {@link #failure(Throwable)} for each operation it performs. Application code should not call these;
     * implementors use them to update internal breaker state.</p>
     *
     * <p>The breaker communicates its decision back to the client through {@link #state()}:</p>
     * <ul>
     *   <li>{@link State#CLOSED} – Normal operation; the client sends requests as usual.</li>
     *   <li>{@link State#HALF_OPEN} – The client probes cautiously (limited traffic) to test recovery.
     *       A successful probe typically transitions the breaker towards {@code CLOSED}.</li>
     *   <li>{@link State#OPEN} – The client short-circuits (fails fast) instead of sending requests
     *       until probing is allowed again.</li>
     * </ul>
     *
     * <p>What counts as a failure?</p>
     * <ul>
     *   <li>Non-2xx HTTP responses (e.g., 429, 5xx) are reported via {@link #failure(HttpResponse)}.</li>
     *   <li>Transport errors (connect/DNS/TLS/timeouts, etc.) are reported via {@link #failure(Throwable)}.</li>
     *   <li>Successful 2xx responses are reported via {@link #success()}.</li>
     * </ul>
     *
     * <p>Vespa provides {@code GracePeriodCircuitBreaker}, a time-based implementation that can be configured to:
     * start probing after a {@code grace} period of continuous failures, and optionally transition to {@code OPEN}
     * after a longer {@code doom} period if failures persist. Example:</p>
     *
     * <pre>{@code
     * var breaker = new GracePeriodCircuitBreaker(
     *         Duration.ofSeconds(10), // start HALF_OPEN probing after ~10s of continuous failures
     *         Duration.ofSeconds(20)  // go OPEN if failures persist for ~20s
     * );
     * var client = FeedClientBuilder.create(endpoint)
     *         .setCircuitBreaker(breaker)
     *         .build();
     * }</pre>
     *
     * <p><strong>Default behavior:</strong> The default circuit-breaker is documented on
     * {@code FeedClientBuilderImpl}. This interface does not prescribe a default.</p>
     */
    interface CircuitBreaker {

        /** A circuit breaker which is always {@link State#CLOSED} (circuit breaking disabled). */
        CircuitBreaker FUSED = () -> State.CLOSED;

        /** Called by the client whenever a successful (2xx) HTTP response is obtained. */
        default void success() { }

        /** Called by the client for any error HTTP response (e.g., 4xx/5xx) considered a failure. */
        default void failure(HttpResponse response) { }

        /** Called by the client when a transport exception occurs attempting to obtain an HTTP response. */
        default void failure(Throwable cause) { }

        /** Returns the current state of the circuit breaker. */
        State state();

        enum State {

            /** Circuit is closed: business as usual. */
            CLOSED,

            /** Circuit is half-open: probing recovery with limited traffic. */
            HALF_OPEN,

            /** Circuit is open: fail fast instead of sending normal requests. */
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
