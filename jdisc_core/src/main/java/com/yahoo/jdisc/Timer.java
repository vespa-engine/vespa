// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.google.inject.ImplementedBy;
import com.yahoo.jdisc.core.SystemTimer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * <p>This class provides access to the current time in milliseconds, as viewed by the {@link Container}. Inject an
 * instance of this class into any component that needs to access time, instead of using
 * <code>System.currentTimeMillis()</code>.</p>
 *
 * @author Simon Thoresen Hult
 */
@ImplementedBy(SystemTimer.class)
public interface Timer {

    /**
     * <p>Returns the current time in milliseconds. Note that while the unit of time of the return value is a
     * millisecond, the granularity of the value depends on the underlying operating system and may be larger. For
     * example, many operating systems measure time in units of tens of milliseconds.</p>
     *
     * <p> See the description of the class <code>Date</code> for a discussion of slight discrepancies that may arise
     * between "computer time" and coordinated universal time (UTC).</p>
     *
     * @return The difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.
     * @see java.util.Date
     */
    long currentTimeMillis();

    /** Convenience method for getting an java.util.Instance from currentTimeMillis(). */
    default Instant currentTime() {
        return Instant.ofEpochMilli(currentTimeMillis());
    }

    /** Return a UTC Clock backed by this timer. */
    default Clock toUtcClock() {
        return new ClockAdapter(this, ZoneOffset.UTC);
    }

    /** Create a Timer backed by the given Clock. */
    static Timer fromClock(Clock clock) {
        return clock::millis;
    }

    class ClockAdapter extends Clock {
        private final Timer timer;
        private final ZoneId zoneId;

        private ClockAdapter(Timer timer, ZoneId zoneId) {
            this.timer = timer;
            this.zoneId = zoneId;
        }

        @Override public ZoneId getZone() { return zoneId; }
        @Override public Clock withZone(ZoneId zone) { return new ClockAdapter(timer, zoneId); }
        @Override public Instant instant() { return timer.currentTime(); }
    }

}
