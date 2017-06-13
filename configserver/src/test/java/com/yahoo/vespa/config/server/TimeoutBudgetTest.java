// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 * @since 5.1
 */
public class TimeoutBudgetTest {
    public static TimeoutBudget day() {
        return new TimeoutBudget(new ManualClock(Instant.now()), Duration.ofDays(1));
    }

    @Test
    public void testTimeLeft() {
        ManualClock clock = new ManualClock();

        TimeoutBudget budget = new TimeoutBudget(clock, Duration.ofMillis(7));
        assertThat(budget.timeLeft().toMillis(), is(7l));
        clock.advance(Duration.ofMillis(1));
        assertThat(budget.timeLeft().toMillis(), is(6l));
        clock.advance(Duration.ofMillis(5));
        assertThat(budget.timeLeft().toMillis(), is(1l));
        assertThat(budget.timeLeft().toMillis(), is(1l));
        clock.advance(Duration.ofMillis(1));
        assertThat(budget.timeLeft().toMillis(), is(0l));
        clock.advance(Duration.ofMillis(5));
        assertThat(budget.timeLeft().toMillis(), is(0l));

        clock.advance(Duration.ofMillis(1));
        assertThat(budget.timesUsed(), is("[0 ms, 1 ms, 5 ms, 0 ms, 1 ms, 5 ms, total: 13 ms]"));
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
        assertThat(budget.timesUsed(), is("[0 ms, 1 ms, 5 ms, 0 ms, 1 ms, 5 ms, total: 13 ms]"));
    }

}
