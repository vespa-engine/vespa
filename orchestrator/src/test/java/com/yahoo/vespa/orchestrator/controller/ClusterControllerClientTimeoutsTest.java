package com.yahoo.vespa.orchestrator.controller;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.test.ManualClock;
import com.yahoo.time.TimeBudget;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;

import static com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts.CONNECT_TIMEOUT;
import static com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts.IN_PROCESS_OVERHEAD;
import static com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts.IN_PROCESS_OVERHEAD_PER_CALL;
import static com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts.MIN_SERVER_TIMEOUT;
import static com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts.NETWORK_OVERHEAD_PER_CALL;
import static com.yahoo.vespa.orchestrator.controller.ClusterControllerClientTimeouts.NUM_CALLS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClusterControllerClientTimeoutsTest {
    // The minimum time left for any invocation of prepareForImmediateJaxRsCall().
    private static final Duration MINIMUM_TIME_LEFT = IN_PROCESS_OVERHEAD_PER_CALL
            .plus(CONNECT_TIMEOUT)
            .plus(NETWORK_OVERHEAD_PER_CALL)
            .plus(MIN_SERVER_TIMEOUT);
    static {
        assertEquals(Duration.ofMillis(160), MINIMUM_TIME_LEFT);
    }

    // The minimum time left (= original time) which is required to allow any requests to the CC.
    private static final Duration MINIMUM_ORIGINAL_TIMEOUT = MINIMUM_TIME_LEFT
            .multipliedBy(NUM_CALLS)
            .plus(IN_PROCESS_OVERHEAD);
    static {
        assertEquals(Duration.ofMillis(420), MINIMUM_ORIGINAL_TIMEOUT);
    }

    private final ManualClock clock = new ManualClock();

    private Duration originalTimeout;
    private TimeBudget timeBudget;
    private ClusterControllerClientTimeouts timeouts;

    private void makeTimeouts(Duration originalTimeout) {
        this.originalTimeout = originalTimeout;
        this.timeBudget = TimeBudget.from(clock, clock.instant(), Optional.of(originalTimeout));
        this.timeouts = new ClusterControllerClientTimeouts("clustername", timeBudget);
    }

    @Before
    public void setUp() {
        makeTimeouts(Duration.ofSeconds(3));
    }

    @Test
    public void makes2RequestsWithMaxProcessingTime() {
        assertEquals(Duration.ofMillis(50), timeouts.getConnectTimeout());
        assertEquals(Duration.ofMillis(1350), timeouts.getReadTimeout());
        assertEquals(Duration.ofMillis(1300), timeouts.getServerTimeout());

        Duration maxProcessingTime = IN_PROCESS_OVERHEAD_PER_CALL
                .plus(CONNECT_TIMEOUT)
                .plus(timeouts.getReadTimeout());
        assertEquals(1450, maxProcessingTime.toMillis());
        clock.advance(maxProcessingTime);

        assertEquals(Duration.ofMillis(50), timeouts.getConnectTimeout());
        assertEquals(Duration.ofMillis(1350), timeouts.getReadTimeout());
        assertEquals(Duration.ofMillis(1300), timeouts.getServerTimeout());

        clock.advance(maxProcessingTime);

        try {
            timeouts.getServerTimeout();
            fail();
        } catch (UncheckedTimeoutException e) {
            assertEquals(
                    "Too little time left (PT0.1S) to call content cluster 'clustername', original timeout was PT3S",
                    e.getMessage());
        }
    }

    @Test
    public void makesAtLeast3RequestsWithShortProcessingTime() {
        assertEquals(Duration.ofMillis(50), timeouts.getConnectTimeout());
        assertEquals(Duration.ofMillis(1350), timeouts.getReadTimeout());
        assertEquals(Duration.ofMillis(1300), timeouts.getServerTimeout());

        Duration shortPocessingTime = Duration.ofMillis(200);
        clock.advance(shortPocessingTime);

        assertEquals(Duration.ofMillis(50), timeouts.getConnectTimeout());
        assertEquals(Duration.ofMillis(1350), timeouts.getReadTimeout());
        assertEquals(Duration.ofMillis(1300), timeouts.getServerTimeout());

        clock.advance(shortPocessingTime);

        assertEquals(Duration.ofMillis(50), timeouts.getConnectTimeout());
        assertEquals(Duration.ofMillis(1350), timeouts.getReadTimeout());
        assertEquals(Duration.ofMillis(1300), timeouts.getServerTimeout());
    }

    @Test
    public void alreadyTimedOut() {
        clock.advance(Duration.ofSeconds(4));

        try {
            timeouts.getServerTimeout();
            fail();
        } catch (UncheckedTimeoutException e) {
            assertEquals(
                    "Exceeded the timeout PT3S against content cluster 'clustername' by PT1S",
                    e.getMessage());
        }
    }

    @Test
    public void justTooLittleTime() {
        clock.advance(originalTimeout.minus(MINIMUM_TIME_LEFT).plus(Duration.ofMillis(1)));
        try {
            timeouts.getServerTimeout();
            fail();
        } catch (UncheckedTimeoutException e) {
            assertEquals(
                    "Server would be given too little time to complete: PT0.009S. Original timeout was PT3S",
                    e.getMessage());
        }
    }

    @Test
    public void justEnoughTime() {
        clock.advance(originalTimeout.minus(MINIMUM_TIME_LEFT));
        timeouts.getServerTimeout();
    }

    @Test
    public void justTooLittleInitialTime() {
        makeTimeouts(MINIMUM_ORIGINAL_TIMEOUT.minus(Duration.ofMillis(1)));
        try {
            timeouts.getServerTimeout();
            fail();
        } catch (UncheckedTimeoutException e) {
            assertEquals(
                    "Server would be given too little time to complete: PT0.0095S. Original timeout was PT0.419S",
                    e.getMessage());
        }
    }

    @Test
    public void justEnoughInitialTime() {
        makeTimeouts(MINIMUM_ORIGINAL_TIMEOUT);
        timeouts.getServerTimeout();
    }
}