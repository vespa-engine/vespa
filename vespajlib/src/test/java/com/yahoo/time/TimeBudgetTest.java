// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.time;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TimeBudgetTest {

    @Test
    public void testBasics() {
        ManualClock clock = new ManualClock();
        clock.setInstant(Instant.ofEpochSecond(0));
        TimeBudget timeBudget = TimeBudget.fromNow(clock, Duration.ofSeconds(10));

        clock.advance(Duration.ofSeconds(7));
        assertEquals(Duration.ofSeconds(3), timeBudget.timeLeftOrThrow().get());

        // Verify that toMillis() of >=1 is fine, but 0 is not.

        clock.setInstant(Instant.ofEpochSecond(9, 999000000));
        assertEquals(1, timeBudget.timeLeftOrThrow().get().toMillis());
        clock.setInstant(Instant.ofEpochSecond(9, 999000001));
        try {
            timeBudget.timeLeftOrThrow();
            fail();
        } catch (UncheckedTimeoutException e) {
            // OK
        }
    }

    @Test
    public void noDeadline() {
        ManualClock clock = new ManualClock();
        clock.setInstant(Instant.ofEpochSecond(0));
        TimeBudget timeBudget = TimeBudget.from(clock, clock.instant(), Optional.empty());

        assertFalse(timeBudget.originalTimeout().isPresent());
        assertFalse(timeBudget.timeLeftOrThrow().isPresent());
        assertFalse(timeBudget.deadline().isPresent());
    }
}