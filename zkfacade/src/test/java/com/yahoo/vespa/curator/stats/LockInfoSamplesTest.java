package com.yahoo.vespa.curator.stats;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LockInfoSamplesTest {
    private final LockInfoSamples samples = new LockInfoSamples(2);
    private ThreadLockInfo threadLockInfo;

    @Test
    public void test() {
        threadLockInfo = new ThreadLockInfo(Thread.currentThread());

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

        Map<String, LockInfo> lockInfos = samples.asList().stream().collect(Collectors.toMap(
                lockInfo -> lockInfo.getLockPath(),
                lockInfo -> lockInfo));
        assertEquals(2, lockInfos.size());

        assertTrue(lockInfos.containsKey("1"));
        assertEquals(Duration.ofSeconds(11), lockInfos.get("1").getStableTotalDuration());

        assertTrue(lockInfos.containsKey("4"));
        assertEquals(Duration.ofSeconds(6), lockInfos.get("4").getStableTotalDuration());
    }

    private boolean maybeSample(String lockPath, int secondsDuration) {
        LockInfo lockInfo = LockInfo.invokingAcquire(threadLockInfo, lockPath, Duration.ofSeconds(1));
        Instant instant = lockInfo.getTimeAcquiredWasInvoked().plus(Duration.ofSeconds(secondsDuration));
        lockInfo.setTerminalState(LockInfo.LockState.RELEASED, instant);
        return samples.maybeSample(lockInfo);
    }

}