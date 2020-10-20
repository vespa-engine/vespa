// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.vespa.curator.Lock;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
    private final String lock2Path = "/lock2/path";
    private static final Duration acquireTimeout = Duration.ofMillis(1000);
    private final Lock lock = new Lock(lockPath, mutex);
    private final Lock lock2 = new Lock(lock2Path, mutex);

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

        var expectedMetrics = new LockMetrics();
        expectedMetrics.setAcquireCount(1);
        expectedMetrics.setCumulativeAcquireCount(1);
        expectedMetrics.setAcquireFailedCount(1);
        expectedMetrics.setCumulativeAcquireFailedCount(1);
        assertLockMetricsIs(expectedMetrics);

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

    private void assertLock2MetricsIs(LockMetrics expected) {
        assertLockMetrics(expected, LockStats.getGlobal().getLockMetricsByPath().get(lock2Path));
    }

    private void assertLockMetricsIs(LockMetrics expected) {
        assertLockMetrics(expected, LockStats.getGlobal().getLockMetricsByPath().get(lockPath));
    }

    private void assertLockMetrics(LockMetrics expected, LockMetrics actual) {
        assertNotNull(actual);

        assertEquals(expected.getCumulativeAcquireCount(), actual.getCumulativeAcquireCount());
        assertEquals(expected.getCumulativeAcquireFailedCount(), actual.getCumulativeAcquireFailedCount());
        assertEquals(expected.getCumulativeAcquireTimedOutCount(), actual.getCumulativeAcquireTimedOutCount());
        assertEquals(expected.getCumulativeAcquireSucceededCount(), actual.getCumulativeAcquireSucceededCount());
        assertEquals(expected.getCumulativeReleaseCount(), actual.getCumulativeReleaseCount());
        assertEquals(expected.getCumulativeReleaseFailedCount(), actual.getCumulativeReleaseFailedCount());
        assertEquals(expected.getCumulativeReentryCount(), actual.getCumulativeReentryCount());
        assertEquals(expected.getCumulativeDeadlockCount(), actual.getCumulativeDeadlockCount());
        assertEquals(expected.getCumulativeNakedReleaseCount(), actual.getCumulativeNakedReleaseCount());
        assertEquals(expected.getCumulativeAcquireWithoutReleaseCount(), actual.getCumulativeAcquireWithoutReleaseCount());
        assertEquals(expected.getCumulativeForeignReleaseCount(), actual.getCumulativeForeignReleaseCount());

        assertEquals(expected.getAndResetAcquireCount(), actual.getAndResetAcquireCount());
        assertEquals(expected.getAndResetAcquireFailedCount(), actual.getAndResetAcquireFailedCount());
        assertEquals(expected.getAndResetAcquireTimedOutCount(), actual.getAndResetAcquireTimedOutCount());
        assertEquals(expected.getAndResetAcquireSucceededCount(), actual.getAndResetAcquireSucceededCount());
        assertEquals(expected.getAndResetReleaseCount(), actual.getAndResetReleaseCount());
        assertEquals(expected.getAndResetReleaseFailedCount(), actual.getAndResetReleaseFailedCount());
        assertEquals(expected.getAndResetReentryCount(), actual.getAndResetReentryCount());
        assertEquals(expected.getAndResetDeadlockCount(), actual.getAndResetDeadlockCount());
        assertEquals(expected.getAndResetNakedReleaseCount(), actual.getAndResetNakedReleaseCount());
        assertEquals(expected.getAndResetAcquireWithoutReleaseCount(), actual.getAndResetAcquireWithoutReleaseCount());
        assertEquals(expected.getAndResetForeignReleaseCount(), actual.getAndResetForeignReleaseCount());
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

        var expectedMetrics = new LockMetrics();
        expectedMetrics.setAcquireCount(1);
        expectedMetrics.setCumulativeAcquireCount(1);
        expectedMetrics.setAcquireTimedOutCount(1);
        expectedMetrics.setCumulativeAcquireTimedOutCount(1);
        assertLockMetricsIs(expectedMetrics);
    }

    @Test
    public void acquired() throws Exception {
        when(mutex.acquire(anyLong(), any())).thenReturn(true);

        lock.acquire(acquireTimeout);
        assertLockMetricsIs(new LockMetrics()
                .setAcquireCount(1)
                .setCumulativeAcquireCount(1)
                .setAcquireSucceededCount(1)
                .setCumulativeAcquireSucceededCount(1));

        // reenter lock
        {
            // NB: non-cumulative counters are reset on fetch
            lock.acquire(acquireTimeout);
            assertLockMetricsIs(new LockMetrics()
                    .setReentryCount(1)
                    .setCumulativeAcquireCount(1)
                    .setCumulativeAcquireSucceededCount(1)
                    .setCumulativeReentryCount(1));

            lock.close();
            assertLockMetricsIs(new LockMetrics()
                    .setCumulativeAcquireCount(1)
                    .setCumulativeAcquireSucceededCount(1)
                    .setCumulativeReentryCount(1));
        }

        // nested lock2
        {
            lock2.acquire(acquireTimeout);
            assertLock2MetricsIs(new LockMetrics()
                    .setAcquireCount(1)
                    .setCumulativeAcquireCount(1)
                    .setAcquireSucceededCount(1)
                    .setCumulativeAcquireSucceededCount(1));

            lock2.close();
            assertLock2MetricsIs(new LockMetrics()
                    .setReleaseCount(1)
                    .setCumulativeAcquireCount(1)
                    .setCumulativeAcquireSucceededCount(1)
                    .setCumulativeReleaseCount(1));
        }

        lock.close();
        assertLockMetricsIs(new LockMetrics()
                .setReleaseCount(1)
                .setCumulativeAcquireCount(1)
                .setCumulativeAcquireSucceededCount(1)
                .setCumulativeReentryCount(1)
                .setCumulativeReleaseCount(1));
    }

    @Test
    public void nestedLocks() throws Exception {
        when(mutex.acquire(anyLong(), any())).thenReturn(true);

        lock.acquire(acquireTimeout);
        lock2.acquire(acquireTimeout);

        List<ThreadLockStats> threadLockStats = LockStats.getGlobal().getThreadLockStats();
        assertEquals(1, threadLockStats.size());
        List<LockAttempt> lockAttempts = threadLockStats.get(0).getOngoingLockAttempts();
        assertEquals(2, lockAttempts.size());
        assertEquals(lockPath, lockAttempts.get(0).getLockPath());
        assertEquals(LockAttempt.LockState.ACQUIRED, lockAttempts.get(0).getLockState());
        assertEquals(lock2Path, lockAttempts.get(1).getLockPath());
        assertEquals(LockAttempt.LockState.ACQUIRED, lockAttempts.get(1).getLockState());

        lock.close();
        lock.close();
    }

    @Test
    public void deadlock() throws Exception {
        var lockPath1 = "/lock/path/1";
        var lockPath2 = "/lock/path/2";

        var lock1 = new Lock(lockPath1, new InterProcessMutexMock());
        var lock2 = new Lock(lockPath2, new InterProcessMutexMock());

        lock2.acquire(acquireTimeout);

        Thread thread = Executors.defaultThreadFactory().newThread(() -> threadMain(lock1, lock2));
        thread.setName("LockTest-async-thread");
        thread.start();

        LockStats globalStats = LockStats.getGlobal();
        ThreadLockStats asyncThreadStats = globalStats.getForThread(thread);
        while (true) {
            Optional<LockAttempt> bottomMostOngoingLockAttempt = asyncThreadStats.getBottomMostOngoingLockAttempt();
            if (bottomMostOngoingLockAttempt.isPresent() &&
                    bottomMostOngoingLockAttempt.get().getLockPath().equals(lockPath2)) {
                break;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) { }
        }

        try {
            lock1.acquire(acquireTimeout);
            fail();
        } catch (UncheckedTimeoutException e) {
            assertEquals("Timed out after waiting PT1S to acquire lock '/lock/path/1'", e.getMessage());
        }

        LockMetrics lockMetrics = LockStats.getGlobal().getLockMetrics("/lock/path/1");
        assertEquals(1, lockMetrics.getAndResetDeadlockCount());
        assertEquals(1, lockMetrics.getCumulativeDeadlockCount());

        // Unlock, which unblocks thread
        lock2.close();
        thread.join();
    }

    private static void threadMain(Lock lock1, Lock lock2) {
        lock1.acquire(acquireTimeout);

        // This will block
        try {
            lock2.acquire(acquireTimeout);
        } catch (UncheckedTimeoutException ignored) {}

        lock2.close();

        lock1.close();
    }

    private static class InterProcessMutexMock implements InterProcessLock {
        private final ReentrantLock lock = new ReentrantLock();
        @Override public void acquire() throws Exception { lock.lock(); }
        @Override public boolean acquire(long time, TimeUnit unit) throws Exception {
            return lock.tryLock(time, unit);
        }
        @Override public void release() throws Exception { lock.unlock(); }
        @Override public boolean isAcquiredInThisProcess() { return lock.isLocked(); }
    }
}
