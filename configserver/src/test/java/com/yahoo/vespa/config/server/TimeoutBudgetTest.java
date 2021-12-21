// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class TimeoutBudgetTest {
    public static TimeoutBudget day() {
        return new TimeoutBudget(new ManualClock(Instant.now()), Duration.ofDays(1));
    }

    @Test
    public void testTimeLeft() {
        ManualClock clock = new ManualClock();

        TimeoutBudget budget = new TimeoutBudget(clock, Duration.ofMillis(7));
        assertThat(budget.timeLeft().toMillis(), is(7L));
        clock.advance(Duration.ofMillis(1));
        assertThat(budget.timeLeft().toMillis(), is(6L));
        clock.advance(Duration.ofMillis(5));
        assertThat(budget.timeLeft().toMillis(), is(1L));
        assertThat(budget.timeLeft().toMillis(), is(1L));
        clock.advance(Duration.ofMillis(1));
        assertThat(budget.timeLeft().toMillis(), is(0L));
        clock.advance(Duration.ofMillis(5));
        assertThat(budget.timeLeft().toMillis(), is(0L));

        clock.advance(Duration.ofMillis(1));
        assertThat(budget.timesUsed(), is("[total: 13 ms]"));
    }

    @Test
    public void testHasTimeLeft() {
        ManualClock clock = new ManualClock();

        TimeoutBudget budget = new TimeoutBudget(clock, Duration.ofMillis(7));
        assertThat(budget.hasTimeLeft(), is(true));
        clock.advance(Duration.ofMillis(1));
        assertThat(budget.hasTimeLeft(), is(true));
        clock.advance(Duration.ofMillis(5));
        assertThat(budget.hasTimeLeft(), is(true));
        assertThat(budget.hasTimeLeft(), is(true));
        clock.advance(Duration.ofMillis(1));
        assertThat(budget.hasTimeLeft(), is(false));
        clock.advance(Duration.ofMillis(5));
        assertThat(budget.hasTimeLeft(), is(false));

        clock.advance(Duration.ofMillis(1));
        assertThat(budget.timesUsed(), is("[total: 13 ms]"));
    }

    @Test
    public void testHasTimeLeftWithLabels() {
        ManualClock clock = new ManualClock();

        TimeoutBudget budget = new TimeoutBudget(clock, Duration.ofMillis(7));
        assertThat(budget.hasTimeLeft("a"), is(true));
        clock.advance(Duration.ofMillis(1));
        assertThat(budget.hasTimeLeft("b"), is(true));
        clock.advance(Duration.ofMillis(5));
        assertThat(budget.hasTimeLeft("c"), is(true));
        assertThat(budget.hasTimeLeft("d"), is(true));

        clock.advance(Duration.ofMillis(1));
        assertThat(budget.timesUsed(), is("[a: 0 ms, b: 1 ms, c: 5 ms, d: 0 ms, total: 7 ms]"));
    }

}
