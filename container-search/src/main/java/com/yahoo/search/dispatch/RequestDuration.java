// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import java.time.Duration;
import java.time.Instant;

/**
 * Contains start and and time. Exposes a duration, and lets you measure the time difference between 2 requests.
 * It does use System.nanoTime to get a steady clock.
 *
 * @author baldersheim
 */
class RequestDuration {
    private final long startTime;
    private long endTime;
    RequestDuration() {
        this(System.nanoTime());
    }
    private RequestDuration(long startTime) {
        this.startTime = startTime;
    }

    RequestDuration complete() {
        endTime = System.nanoTime();
        return this;
    }
    private RequestDuration complete(long duration) {
        endTime = startTime + duration;
        return this;
    }
    Duration duration() {
        return Duration.ofNanos(endTime - startTime);
    }
    Duration difference(RequestDuration prev) {
        return Duration.ofNanos(Math.abs(endTime - prev.endTime));
    }
    static RequestDuration of(Duration duration) {
        return new RequestDuration().complete(duration.toNanos());
    }
    static RequestDuration of(Instant sinceEpoch, Duration duration) {
        return new RequestDuration(sinceEpoch.toEpochMilli()*1_000_000).complete(duration.toNanos());
    }
}
