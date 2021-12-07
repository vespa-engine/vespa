// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.FeedClient.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static ai.vespa.feed.client.FeedClient.CircuitBreaker.State.CLOSED;
import static ai.vespa.feed.client.FeedClient.CircuitBreaker.State.HALF_OPEN;
import static ai.vespa.feed.client.FeedClient.CircuitBreaker.State.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class GracePeriodCircuitBreakerTest {

    @Test
    void testCircuitBreaker() {
        AtomicLong now = new AtomicLong(0);
        long SECOND = 1000;
        CircuitBreaker breaker = new GracePeriodCircuitBreaker(now::get, Duration.ofSeconds(1), Duration.ofMinutes(1));
        Throwable error = new Error();

        assertEquals(CLOSED, breaker.state(), "Initial state is closed");

        now.addAndGet(100 * SECOND);
        assertEquals(CLOSED, breaker.state(), "State is closed after some time without activity");

        breaker.success();
        assertEquals(CLOSED, breaker.state(), "State is closed after a success");

        now.addAndGet(100 * SECOND);
        assertEquals(CLOSED, breaker.state(), "State is closed some time after a success");

        breaker.failure(error);
        assertEquals(CLOSED, breaker.state(), "State is closed right after a failure");

        now.addAndGet(SECOND);
        assertEquals(CLOSED, breaker.state(), "State is closed until grace period has passed");

        now.addAndGet(1);
        assertEquals(HALF_OPEN, breaker.state(), "State is half-open when grace period has passed");

        breaker.success();
        assertEquals(CLOSED, breaker.state(), "State is closed after a new success");

        breaker.failure(error);
        now.addAndGet(60 * SECOND);
        assertEquals(HALF_OPEN, breaker.state(), "State is half-open until doom period has passed");

        now.addAndGet(1);
        assertEquals(OPEN, breaker.state(), "State is open when doom period has passed");

        breaker.success();
        assertEquals(OPEN, breaker.state(), "State remains open in spite of new successes");
    }

}
