// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import com.yahoo.vespa.curator.Lock;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakon
 */
public class LockTest {
    private final InterProcessLock mutex = mock(InterProcessLock.class);
    private final String lockPath = "/lock/path";
    private final Duration acquireTimeout = Duration.ofSeconds(10);
    private final Lock lock = new Lock(lockPath, mutex);

    @Before
    public void setUp() {
        LockStats.clearForTesting();
    }

    @Test
    public void acquireThrows() throws Exception {
        Exception exception = new Exception("example curator exception");
        when(mutex.acquire(anyLong(), any())).thenThrow(exception);

        try {
            lock.acquire(acquireTimeout);
            fail();
        } catch (Exception e) {
            assertSame(e.getCause(), exception);
        }

        var expectedCounters = new LockCounters();
        expectedCounters.invokeAcquireCount.set(1);
        expectedCounters.acquireFailedCount.set(1);
        assertEquals(Map.of(lockPath, expectedCounters), LockStats.getGlobal().getLockCountersByPath());

        List<LockAttempt> slowLockAttempts = LockStats.getGlobal().getLockAttemptSamples();
        assertEquals(1, slowLockAttempts.size());
        LockAttempt slowLockAttempt = slowLockAttempts.get(0);
        assertEquals(acquireTimeout, slowLockAttempt.getAcquireTimeout());
        Optional<String> stackTrace = slowLockAttempt.getStackTrace();
        assertTrue(stackTrace.isPresent());
        assertTrue("bad stacktrace: " + stackTrace.get(), stackTrace.get().contains(".Lock.acquire(Lock.java"));
        assertEquals(LockAttempt.LockState.ACQUIRE_FAILED, slowLockAttempt.getLockState());
        assertTrue(slowLockAttempt.getTimeTerminalStateWasReached().isPresent());

        List<ThreadLockStats> threadLockStatsList = LockStats.getGlobal().getThreadLockStats();
        assertEquals(1, threadLockStatsList.size());
        ThreadLockStats threadLockStats = threadLockStatsList.get(0);
        assertEquals(0, threadLockStats.getOngoingLockAttempts().size());
    }

    @Test
    public void acquireTimesOut() throws Exception {
        when(mutex.acquire(anyLong(), any())).thenReturn(false);

        try {
            lock.acquire(acquireTimeout);
            fail();
        } catch (Exception e) {
            assertTrue("unexpected exception: " + e.getMessage(), e.getMessage().contains("Timed out"));
        }

        var expectedCounters = new LockCounters();
        expectedCounters.invokeAcquireCount.set(1);
        expectedCounters.acquireTimedOutCount.set(1);
        assertEquals(Map.of(lockPath, expectedCounters), LockStats.getGlobal().getLockCountersByPath());
    }

    @Test
    public void acquired() throws Exception {
        when(mutex.acquire(anyLong(), any())).thenReturn(true);

        lock.acquire(acquireTimeout);

        var expectedCounters = new LockCounters();
        expectedCounters.invokeAcquireCount.set(1);
        expectedCounters.lockAcquiredCount.set(1);
        expectedCounters.inCriticalRegionCount.set(1);
        assertEquals(Map.of(lockPath, expectedCounters), LockStats.getGlobal().getLockCountersByPath());

        // reenter
        lock.acquire(acquireTimeout);
        expectedCounters.invokeAcquireCount.set(2);
        expectedCounters.lockAcquiredCount.set(2);
        expectedCounters.inCriticalRegionCount.set(2);

        // inner-most closes
        lock.close();
        expectedCounters.inCriticalRegionCount.set(1);
        expectedCounters.locksReleasedCount.set(1);
        assertEquals(Map.of(lockPath, expectedCounters), LockStats.getGlobal().getLockCountersByPath());

        // outer-most closes
        lock.close();
        expectedCounters.inCriticalRegionCount.set(0);
        expectedCounters.locksReleasedCount.set(2);
        assertEquals(Map.of(lockPath, expectedCounters), LockStats.getGlobal().getLockCountersByPath());
    }

    @Test
    public void nestedLocks() throws Exception {
        when(mutex.acquire(anyLong(), any())).thenReturn(true);

        String lockPath2 = "/lock/path/2";
        Lock lock2 = new Lock(lockPath2, mutex);

        lock.acquire(acquireTimeout);
        lock2.acquire(acquireTimeout);

        List<ThreadLockStats> threadLockStats = LockStats.getGlobal().getThreadLockStats();
        assertEquals(1, threadLockStats.size());
        List<LockAttempt> lockAttempts = threadLockStats.get(0).getOngoingLockAttempts();
        assertEquals(2, lockAttempts.size());
        assertEquals(lockPath, lockAttempts.get(0).getLockPath());
        assertEquals(LockAttempt.LockState.ACQUIRED, lockAttempts.get(0).getLockState());
        assertEquals(lockPath2, lockAttempts.get(1).getLockPath());
        assertEquals(LockAttempt.LockState.ACQUIRED, lockAttempts.get(1).getLockState());

        lock.close();
        lock.close();
    }
}
