package com.yahoo.vespa.curator.stats;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

public class LockTest {
    private final InterProcessLock mutex = mock(InterProcessLock.class);
    private final String lockPath = "/lock/path";
    private final Duration acquireTimeout = Duration.ofSeconds(10);
    private final Lock lock = new Lock(lockPath, mutex);

    @Before
    public void setUp() {
        ThreadLockInfo.clearStaticDataForTesting();
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
        assertEquals(Map.of(lockPath, expectedCounters), ThreadLockInfo.getLockCountersByPath());

        List<LockInfo> slowLockInfos = ThreadLockInfo.getLockInfoSamples();
        assertEquals(1, slowLockInfos.size());
        LockInfo slowLockInfo = slowLockInfos.get(0);
        assertEquals(acquireTimeout, slowLockInfo.getAcquireTimeout());
        Optional<String> stackTrace = slowLockInfo.getStackTrace();
        assertTrue(stackTrace.isPresent());
        assertTrue("bad stacktrace: " + stackTrace.get(), stackTrace.get().contains(".Lock.acquire(Lock.java"));
        assertEquals(LockInfo.LockState.ACQUIRE_FAILED, slowLockInfo.getLockState());
        assertTrue(slowLockInfo.getTimeTerminalStateWasReached().isPresent());

        List<ThreadLockInfo> threadLockInfos = ThreadLockInfo.getThreadLockInfos();
        assertEquals(1, threadLockInfos.size());
        ThreadLockInfo threadLockInfo = threadLockInfos.get(0);
        assertEquals(0, threadLockInfo.getLockInfos().size());
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
        assertEquals(Map.of(lockPath, expectedCounters), ThreadLockInfo.getLockCountersByPath());
    }

    @Test
    public void acquired() throws Exception {
        when(mutex.acquire(anyLong(), any())).thenReturn(true);

        lock.acquire(acquireTimeout);

        var expectedCounters = new LockCounters();
        expectedCounters.invokeAcquireCount.set(1);
        expectedCounters.lockAcquiredCount.set(1);
        expectedCounters.inCriticalRegionCount.set(1);
        assertEquals(Map.of(lockPath, expectedCounters), ThreadLockInfo.getLockCountersByPath());

        // reenter
        lock.acquire(acquireTimeout);
        expectedCounters.invokeAcquireCount.set(2);
        expectedCounters.lockAcquiredCount.set(2);
        expectedCounters.inCriticalRegionCount.set(2);

        // inner-most closes
        lock.close();
        expectedCounters.inCriticalRegionCount.set(1);
        expectedCounters.locksReleasedCount.set(1);
        assertEquals(Map.of(lockPath, expectedCounters), ThreadLockInfo.getLockCountersByPath());

        // outer-most closes
        lock.close();
        expectedCounters.inCriticalRegionCount.set(0);
        expectedCounters.locksReleasedCount.set(2);
        assertEquals(Map.of(lockPath, expectedCounters), ThreadLockInfo.getLockCountersByPath());
    }

    @Test
    public void nestedLocks() throws Exception {
        when(mutex.acquire(anyLong(), any())).thenReturn(true);

        String lockPath2 = "/lock/path/2";
        Lock lock2 = new Lock(lockPath2, mutex);

        lock.acquire(acquireTimeout);
        lock2.acquire(acquireTimeout);

        List<ThreadLockInfo> threadLockInfos = ThreadLockInfo.getThreadLockInfos();
        assertEquals(1, threadLockInfos.size());
        List<LockInfo> lockInfos = threadLockInfos.get(0).getLockInfos();
        assertEquals(2, lockInfos.size());
        assertEquals(lockPath, lockInfos.get(0).getLockPath());
        assertEquals(LockInfo.LockState.ACQUIRED, lockInfos.get(0).getLockState());
        assertEquals(lockPath2, lockInfos.get(1).getLockPath());
        assertEquals(LockInfo.LockState.ACQUIRED, lockInfos.get(1).getLockState());

        lock.close();
        lock.close();
    }
}
