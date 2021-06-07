// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Breaks the circuit when no successes have been recorded for a specified time.
 */
public class GracePeriodCircuitBreaker implements FeedClient.CircuitBreaker {

    private static final Logger log = Logger.getLogger(GracePeriodCircuitBreaker.class.getName());

    private final AtomicLong lastSuccessMillis = new AtomicLong(0); // Trigger if first response is a failure.
    private final AtomicBoolean halfOpen = new AtomicBoolean(false);
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final Clock clock;
    private final long graceMillis;
    private final long doomMillis;

    GracePeriodCircuitBreaker(Clock clock, Duration grace, Duration doom) {
        if (grace.isNegative())
            throw new IllegalArgumentException("Grace delay must be non-negative");

        if (doom.isNegative())
            throw new IllegalArgumentException("Doom delay must be non-negative");

        this.clock = requireNonNull(clock);
        this.graceMillis = grace.toMillis();
        this.doomMillis = doom.toMillis();
    }

    @Override
    public void success() {
        lastSuccessMillis.set(clock.millis());
        if (halfOpen.compareAndSet(true, false))
            log.log(INFO, "Circuit breaker is now closed");
    }

    @Override
    public void failure() {
        long nowMillis = clock.millis();
        if (lastSuccessMillis.get() < nowMillis - doomMillis && open.compareAndSet(false, true))
            log.log(WARNING, "Circuit breaker is now open");

        if (lastSuccessMillis.get() < nowMillis - graceMillis && halfOpen.compareAndSet(false, true))
            log.log(INFO, "Circuit breaker is now half-open");
    }

    @Override
    public State state() {
        return open.get() ? State.OPEN : halfOpen.get() ? State.HALF_OPEN : State.CLOSED;
    }

}
