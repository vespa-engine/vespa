// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.time;

import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A TimeBudget can be used to track the time of an ongoing operation with a timeout.
 *
 * @author hakon
 */
public class TimeBudget {
    private final Clock clock;
    private final Instant start;
    private final Duration timeout;

    /** Returns a TimeBudget with a start time of now, and with the given timeout. */
    public static TimeBudget fromNow(Clock clock, Duration timeout) {
        return new TimeBudget(clock, clock.instant(), timeout);
    }

    private TimeBudget(Clock clock, Instant start, Duration timeout) {
        this.clock = clock;
        this.start = start;
        this.timeout = makeNonNegative(timeout);
    }

    /** Returns time since start. */
    public Duration timePassed() {
        return nonNegativeBetween(start, clock.instant());
    }

    /** Returns the original timeout. */
    public Duration originalTimeout() {
        return timeout;
    }

    /**
     * Returns the time until deadline.
     *
     * @return time until deadline. It's toMillis() is guaranteed to be positive.
     * @throws UncheckedTimeoutException if the deadline has been reached or passed.
     */
    public Duration timeLeftOrThrow() {
        Instant now = clock.instant();
        Duration left = Duration.between(now, start.plus(timeout));
        if (left.toMillis() <= 0) {
            throw new UncheckedTimeoutException("Time since start " + nonNegativeBetween(start, now) +
                    " exceeds timeout " + timeout);
        }

        return left;
    }

    private static Duration nonNegativeBetween(Instant start, Instant end) {
        return makeNonNegative(Duration.between(start, end));
    }

    private static Duration makeNonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
