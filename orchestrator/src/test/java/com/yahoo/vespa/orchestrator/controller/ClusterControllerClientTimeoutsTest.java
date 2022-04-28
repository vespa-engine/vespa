// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.test.ManualClock;
import com.yahoo.time.TimeBudget;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;

import static com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts.CONNECT_TIMEOUT;
import static com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts.DOWNSTREAM_OVERHEAD;
import static com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts.MIN_SERVER_TIMEOUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClusterControllerClientTimeoutsTest {

    // The minimum time that allows for a single RPC to CC.
    private static final Duration MINIMUM_TIME_LEFT = CONNECT_TIMEOUT
            .plus(DOWNSTREAM_OVERHEAD)
            .plus(MIN_SERVER_TIMEOUT);
    static {
        assertEquals(Duration.ofMillis(500), MINIMUM_TIME_LEFT);
    }

    private final ManualClock clock = new ManualClock();

    private Duration originalTimeout;
    private TimeBudget timeBudget;
    private ClusterControllerClientTimeouts timeouts;

    private void makeTimeouts(Duration originalTimeout) {
        this.originalTimeout = originalTimeout;
        this.timeBudget = TimeBudget.from(clock, clock.instant(), Optional.of(originalTimeout));
        this.timeouts = new ClusterControllerClientTimeouts(timeBudget);
    }

    @Before
    public void setUp() {
        makeTimeouts(Duration.ofSeconds(3));
    }

    @Test
    public void makesManyRequestsWithShortProcessingTime() {
        assertEquals(Duration.ofMillis(100), timeouts.connectTimeout());
        assertEquals(Duration.ofMillis(2900), timeouts.readBudget().timeLeftOrThrow().get());
        assertEquals(Duration.ofMillis(2600), timeouts.getServerTimeoutOrThrow());

        clock.advance(Duration.ofMillis(100));

        assertEquals(Duration.ofMillis(100), timeouts.connectTimeout());
        assertEquals(Duration.ofMillis(2800), timeouts.readBudget().timeLeftOrThrow().get());
        assertEquals(Duration.ofMillis(2500), timeouts.getServerTimeoutOrThrow());

        clock.advance(Duration.ofMillis(100));

        assertEquals(Duration.ofMillis(100), timeouts.connectTimeout());
        assertEquals(Duration.ofMillis(2700), timeouts.readBudget().timeLeftOrThrow().get());
        assertEquals(Duration.ofMillis(2400), timeouts.getServerTimeoutOrThrow());
    }

    @Test
    public void alreadyTimedOut() {
        clock.advance(Duration.ofSeconds(4));
        try {
            timeouts.getServerTimeoutOrThrow();
            fail();
        } catch (UncheckedTimeoutException e) {
            assertEquals("Timed out after PT3S", e.getMessage());
        }
    }

    @Test
    public void justTooLittleTime() {
        clock.advance(originalTimeout.minus(MINIMUM_TIME_LEFT).plus(Duration.ofMillis(1)));
        try {
            timeouts.getServerTimeoutOrThrow();
            fail();
        } catch (UncheckedTimeoutException e) {
            assertEquals("Timed out after PT3S", e.getMessage());
        }
    }

    @Test
    public void justEnoughTime() {
        clock.advance(originalTimeout.minus(MINIMUM_TIME_LEFT));
        timeouts.getServerTimeoutOrThrow();
    }

}
