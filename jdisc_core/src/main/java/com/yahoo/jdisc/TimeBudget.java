// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import javax.annotation.concurrent.Immutable;
import java.time.Duration;
import java.time.Instant;

/**
 * A TimeBudget tracks the current time compared to a start time and deadline.
 *
 * @author hakon
 */
@Immutable
public class TimeBudget {
    private final Timer timer;
    private final Instant start;
    private final Duration timeout;

    /** Returns a TimeBudget with a start time of now, and with the given timeout. */
    public static TimeBudget fromNow(Timer timer, Duration timeout) {
        return new TimeBudget(timer, timer.currentTime(), timeout);
    }

    private TimeBudget(Timer timer, Instant start, Duration timeout) {
        this.timer = timer;
        this.start = start;
        this.timeout = timeout;
    }

    /** Time until 'headroom' before deadline. Guaranteed to be non-negative. */
    public Duration timeBeforeDeadline(Duration headroom) {
        return nonNegativeBetween(now(), deadline().minus(headroom));
    }

    /** Returns the original timeout. */
    public Duration originalTimeout() {
        return timeout;
    }

    private static Duration nonNegativeBetween(Instant start, Instant end) {
        return makeNonNegative(Duration.between(start, end));
    }

    private static Duration makeNonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private Instant now() {
        return timer.currentTime();
    }

    private Instant deadline() {
        return start.plus(timeout);
    }
}
