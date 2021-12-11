// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.HttpResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Breaks the circuit when no successes have been recorded for a specified time.
 *
 * @author jonmv
 */
public class GracePeriodCircuitBreaker implements FeedClient.CircuitBreaker {

    private static final Logger log = Logger.getLogger(GracePeriodCircuitBreaker.class.getName());
    private static final long NEVER = 1L << 60;

    private final AtomicLong failingSinceMillis = new AtomicLong(NEVER);
    private final AtomicBoolean halfOpen = new AtomicBoolean(false);
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final LongSupplier clock;
    private final AtomicReference<String> detail = new AtomicReference<>();
    private final long graceMillis;
    private final long doomMillis;

    /**
     * Creates a new circuit breaker with the given grace periods.
     * @param grace the period of consecutive failures before state changes to half-open.
     */
    public GracePeriodCircuitBreaker(Duration grace) {
        this(System::currentTimeMillis, grace, null);
    }

    /**
     * Creates a new circuit breaker with the given grace periods.
     * @param grace the period of consecutive failures before state changes to half-open.
     * @param doom the period of consecutive failures before shutting down.
     */
    public GracePeriodCircuitBreaker(Duration grace, Duration doom) {
        this(System::currentTimeMillis, grace, doom);
        if (doom.isNegative())
            throw new IllegalArgumentException("Doom delay must be non-negative");
    }

    GracePeriodCircuitBreaker(LongSupplier clock, Duration grace, Duration doom) {
        if (grace.isNegative())
            throw new IllegalArgumentException("Grace delay must be non-negative");

        this.clock = requireNonNull(clock);
        this.graceMillis = grace.toMillis();
        this.doomMillis = doom == null ? -1 : doom.toMillis();
    }

    @Override
    public void success() {
        failingSinceMillis.set(NEVER);
        if ( ! open.get() && halfOpen.compareAndSet(true, false))
            log.log(FINE, "Circuit breaker is now closed");
    }

    @Override
    public void failure(HttpResponse response) {
        failure(response.toString());
    }

    @Override
    public void failure(Throwable cause) {
        failure(cause.toString());
    }

    private void failure(String detail) {
        if (failingSinceMillis.compareAndSet(NEVER, clock.getAsLong()))
            this.detail.set(detail);
    }

    @Override
    public State state() {
        long failingMillis = clock.getAsLong() - failingSinceMillis.get();
        if (failingMillis > graceMillis && halfOpen.compareAndSet(false, true))
            log.log(INFO, "Circuit breaker is now half-open, as no requests have succeeded for the " +
                          "last " + failingMillis + "ms. The server will be pinged to see if it recovers" +
                          (doomMillis >= 0 ? ", but this client will give up if no successes are observed within " + doomMillis + "ms" : "") +
                          ". First failure was '" + detail.get() + "'.");

        if (doomMillis >= 0 && failingMillis > doomMillis && open.compareAndSet(false, true))
            log.log(WARNING, "Circuit breaker is now open, after " + doomMillis + "ms of failing request, " +
                             "and this client will give up and abort its remaining feed operations.");

        return open.get() ? State.OPEN : halfOpen.get() ? State.HALF_OPEN : State.CLOSED;
    }

}
