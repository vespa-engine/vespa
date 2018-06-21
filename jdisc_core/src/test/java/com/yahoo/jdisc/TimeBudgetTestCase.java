// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TimeBudgetTestCase {
    private final Timer timer = mock(Timer.class);

    @Test
    public void testBasics() {
        when(timer.currentTime()).thenReturn(Instant.ofEpochSecond(0));
        TimeBudget timeBudget = TimeBudget.fromNow(timer, Duration.ofSeconds(10));

        when(timer.currentTime()).thenReturn(Instant.ofEpochSecond(7));
        assertEquals(Duration.ofSeconds(3), timeBudget.timeBeforeDeadline(Duration.ofSeconds(0)));
        assertEquals(Duration.ofSeconds(1), timeBudget.timeBeforeDeadline(Duration.ofSeconds(2)));
        assertEquals(Duration.ofSeconds(0), timeBudget.timeBeforeDeadline(Duration.ofSeconds(5)));

        when(timer.currentTime()).thenReturn(Instant.ofEpochSecond(11));
        assertEquals(Duration.ofSeconds(0), timeBudget.timeBeforeDeadline(Duration.ofSeconds(0)));
    }
}