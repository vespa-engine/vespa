// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.Timer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A {@link Timer} to be used in tests when the advancement of time needs to be controlled.
 *
 * @author hakonhall
 */
public class TestTimer implements Timer {
    private Instant instant;

    public TestTimer() {
        this(Instant.EPOCH);
    }

    public TestTimer(Instant instant) {
        this.instant = Objects.requireNonNull(instant);
    }

    public void setMillis(long millisSinceEpoch) {
        instant = Instant.ofEpochMilli(millisSinceEpoch);
    }

    public void advanceMillis(long millis) { advance(Duration.ofMillis(millis)); }
    public void advanceSeconds(long seconds) { advance(Duration.ofSeconds(seconds)); }
    public void advanceMinutes(long minutes) { advance(Duration.ofMinutes(minutes)); }
    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    @Override
    public Instant currentTime() {
        return instant;
    }

    @Override
    public long currentTimeMillis() {
        return instant.toEpochMilli();
    }
}
