// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Breaks the circuit when no successes have been recorded for a specified time.
 *
 * @author jonmv
 */
public class GracePeriodCircuitBreaker implements FeedClient.CircuitBreaker {

    private static final Logger log = Logger.getLogger(GracePeriodCircuitBreaker.class.getName());

    private final AtomicBoolean halfOpen = new AtomicBoolean(false);
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final LongSupplier nanoClock;
    private final long never;
    private final AtomicLong failingSinceNanos;
    private final AtomicReference<String> detail = new AtomicReference<>();
    private final long graceNanos;
    private final long doomNanos;

    /**
     * Creates a new circuit breaker with the given grace periods.
     * @param grace the period of consecutive failures before state changes to half-open.
     */
    public GracePeriodCircuitBreaker(Duration grace) {
        this(System::nanoTime, grace, null);
    }

    /**
     * Creates a new circuit breaker with the given grace periods.
     * @param grace the period of consecutive failures before state changes to half-open.
     * @param doom the period of consecutive failures before shutting down.
     */
    public GracePeriodCircuitBreaker(Duration grace, Duration doom) {
        this(System::nanoTime, grace, doom);
        if (doom.isNegative())
            throw new IllegalArgumentException("Doom delay must be non-negative");
    }

    GracePeriodCircuitBreaker(LongSupplier nanoClock, Duration grace, Duration doom) {
        if (grace.isNegative())
            throw new IllegalArgumentException("Grace delay must be non-negative");

        this.nanoClock = requireNonNull(nanoClock);
        this.never = nanoClock.getAsLong() + (1L << 60);
        this.graceNanos = grace.toNanos();
        this.doomNanos = doom == null ? -1 : doom.toNanos();
        this.failingSinceNanos = new AtomicLong(never);
    }

    @Override
    public void success() {
        failingSinceNanos.set(never);
        if ( ! open.get() && halfOpen.compareAndSet(true, false))
            log.log(INFO, "Circuit breaker is now closed, after a request was successful");
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
        if (failingSinceNanos.compareAndSet(never, nanoClock.getAsLong()))
            this.detail.set(detail);
    }

    @Override
    public State state() {
        long failingNanos = nanoClock.getAsLong() - failingSinceNanos.get();
        if (failingNanos > graceNanos && halfOpen.compareAndSet(false, true))
            log.log(INFO, "Circuit breaker is now half-open, as no requests have succeeded for the " +
                          "last " + failingNanos / 1_000_000 + "ms. The server will be pinged to see if it recovers" +
                          (doomNanos >= 0 ? ", but this client will give up if no successes are observed within " + doomNanos / 1_000_000 + "ms" : "") +
                          ". First failure was '" + detail.get() + "'.");

        if (doomNanos >= 0 && failingNanos > doomNanos && open.compareAndSet(false, true))
            log.log(WARNING, "Circuit breaker is now open, after " + doomNanos / 1_000_000 + "ms of failing request, " +
                             "and this client will give up and abort its remaining feed operations.");

        return open.get() ? State.OPEN : halfOpen.get() ? State.HALF_OPEN : State.CLOSED;
    }

}
