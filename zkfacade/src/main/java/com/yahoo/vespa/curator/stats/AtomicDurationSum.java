// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An instance of {@link AtomicDurationSum} provides atomic operations on a {@link Duration}
 * and integer counter doublet:  You can add a duration and increment the counter atomically,
 * and get the doublet and reset the duration and counter to zero atomically.
 *
 * <p>The duration and counter must be small:  You can add the equivalent of 16M durations of 1 minute each:
 * The cumulative duration must be between -17 and 17 years, and the maximum count is 16M.
 * The duration will have millisecond resolution.  Overflow of count affects duration.</p>
 *
 * <p>Motivation: Metrics are snapshot and reset to zero every minute.  Durations of typically 1 minute
 * are then summed to produce a cumulative {@link Duration} and an associated count, both of which are
 * therefore small numbers and can be represented in a compact and atomic form.  The alternative is to
 * use synchronization (which is slow) or allow inconsistencies between the duration and count
 * (e.g. a sum of 2 latencies but a count of 1 makes the metrics noisy).</p>
 *
 * @author hakon
 */
public class AtomicDurationSum {

    // Why 40?  The duration-part requires 16 bits to represent 1 minute.  If we require 1 minute
    // durations can be added until both the duration-part and count-part are full, the remaining
    // 48 bits must be divided equally, hence 16 + 24 = 40.  Seems to give a nice balance.
    static final long DURATION_BITS = 40;
    static final long COUNT_BITS = Long.SIZE - DURATION_BITS;
    static final long DURATION_MASK = -1L << COUNT_BITS;
    static final long COUNT_MASK = -1L >>> DURATION_BITS;
    // The most significant bit of duration is a sign bit, which complicates the initializer.
    static final long MIN_DURATION = -1L << (DURATION_BITS - 1);
    static final long MAX_DURATION = (DURATION_MASK << 1) >>> (COUNT_BITS + 1);
    static final long MAX_COUNT = COUNT_MASK;
    static final long MIN_COUNT = 0L;

    private static final long ZERO_DURATION_AND_COUNT = 0L;

    // Representation:
    //  - A signed long of 40 bits storing the duration in milliseconds
    //  - An unsigned int of 24 bits storing the count
    private final AtomicLong encodedAtomic = new AtomicLong(ZERO_DURATION_AND_COUNT);

    /** Initializes to zero duration and count. */
    public AtomicDurationSum() {}

    /** Add duration and increment count. */
    void add(Duration duration) {
        encodedAtomic.addAndGet(encodeDuration(duration) | 1L);
    }

    public DurationSum get() {
        long snapshot = encodedAtomic.get();
        return new DurationSum(decodeDuration(snapshot), decodeCount(snapshot));
    }

    /** Get the current {@link DurationSum} and reset the duration and count doublet to zero. */
    public DurationSum getAndReset() {
        long snapshot = encodedAtomic.getAndSet(ZERO_DURATION_AND_COUNT);
        return new DurationSum(decodeDuration(snapshot), decodeCount(snapshot));
    }

    static long encodeDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < MIN_DURATION || millis > MAX_DURATION) {
            throw new IllegalArgumentException("Duration outside legal range: " + duration);
        }

        return millis << COUNT_BITS;
    }

    static Duration decodeDuration(long encoded) {
        return Duration.ofMillis(encoded >> COUNT_BITS);
    }

    static int decodeCount(long encoded) {
        return (int) (encoded & COUNT_MASK);
    }

    @Override
    public String toString() {
        return get().toString();
    }
}
