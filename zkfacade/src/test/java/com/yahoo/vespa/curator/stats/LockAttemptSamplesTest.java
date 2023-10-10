// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hakon
 */
public class LockAttemptSamplesTest {
    private final LockAttemptSamples samples = new LockAttemptSamples(2);
    private ThreadLockStats threadLockStats;

    @Test
    public void test() {
        threadLockStats = new ThreadLockStats(Thread.currentThread());

        assertTrue(maybeSample("1", 10));

        // new sample has longer duration
        assertTrue(maybeSample("1", 11));

        // new sample has shorter duration
        assertFalse(maybeSample("1", 10));

        // new path, will be added
        assertTrue(maybeSample("2", 5));

        // new path, too low duration be added
        assertFalse(maybeSample("3", 4));

        // new path, expels "2"
        assertTrue(maybeSample("4", 6));

        Map<String, LockAttempt> lockAttempts = samples.asList().stream().collect(Collectors.toMap(
                lockAttempt -> lockAttempt.getLockPath(),
                lockAttempt -> lockAttempt));
        assertEquals(2, lockAttempts.size());

        assertTrue(lockAttempts.containsKey("1"));
        assertEquals(Duration.ofSeconds(11), lockAttempts.get("1").getStableTotalDuration());

        assertTrue(lockAttempts.containsKey("4"));
        assertEquals(Duration.ofSeconds(6), lockAttempts.get("4").getStableTotalDuration());
    }

    private boolean maybeSample(String lockPath, int secondsDuration) {
        LockAttempt lockAttempt = LockAttempt.invokingAcquire(threadLockStats, lockPath,
                Duration.ofSeconds(1), new LockMetrics(), false);
        Instant instant = lockAttempt.getTimeAcquiredWasInvoked().plus(Duration.ofSeconds(secondsDuration));
        lockAttempt.setTerminalState(LockAttempt.LockState.RELEASED, instant);
        return samples.maybeSample(lockAttempt);
    }

}
