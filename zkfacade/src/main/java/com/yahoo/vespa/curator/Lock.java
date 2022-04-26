// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.path.Path;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.curator.stats.LockStats;
import com.yahoo.vespa.curator.stats.ThreadLockStats;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A cluster-wide re-entrant mutex which is released on (the last symmetric) close.
 *
 * Re-entrancy is limited to the instance of this. To ensure re-entrancy callers should access the lock through
 * {@link Curator#lock(Path, Duration)} instead of constructing this directly.
 *
 * @author bratseth
 */
public class Lock implements Mutex {

    private final Object monitor = new Object();
    private long nextSequenceNumber = 0;
    private final Map<Long, Long> reentriesByThreadId = new HashMap<>();

    private final InterProcessLock mutex;
    private final String lockPath;

    public Lock(String lockPath, Curator curator) {
        this(lockPath, curator.createMutex(lockPath));
    }

    /** Public for testing only */
    public Lock(String lockPath, InterProcessLock mutex) {
        this.lockPath = lockPath;
        this.mutex = mutex;
    }

    /** Take the lock with the given timeout. This may be called multiple times from the same thread - each matched by a close */
    public void acquire(Duration timeout) throws UncheckedTimeoutException {
        ThreadLockStats threadLockStats = LockStats.getForCurrentThread();
        threadLockStats.invokingAcquire(lockPath, timeout);

        final boolean acquired;
        try {
            acquired = mutex.acquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            threadLockStats.acquireFailed();
            throw new RuntimeException("Exception acquiring lock '" + lockPath + "'", e);
        }

        if (!acquired) {
            threadLockStats.acquireTimedOut();
            throw new UncheckedTimeoutException("Timed out after waiting " + timeout +
                    " to acquire lock '" + lockPath + "'");
        }

        invoke(+1L, threadLockStats::lockAcquired);
    }

    @FunctionalInterface
    private interface TriConsumer {
        void accept(String lockId, long reentryCountDiff, Map<Long, Long> reentriesByThreadId);
    }

    // TODO(hakon): Remove once debugging is unnecessary
    private void invoke(long reentryCountDiff, TriConsumer consumer) {
        long threadId = Thread.currentThread().getId();
        final long sequenceNumber;
        final Map<Long, Long> reentriesByThreadIdCopy;
        synchronized (monitor) {
            sequenceNumber = nextSequenceNumber++;
            reentriesByThreadId.merge(threadId, reentryCountDiff, (oldValue, argumentValue) -> {
                long sum = oldValue + argumentValue /* == reentryCountDiff */;
                if (sum == 0) {
                    // Remove from map
                    return null;
                } else {
                    return sum;
                }
            });
            reentriesByThreadIdCopy = Map.copyOf(reentriesByThreadId);
        }

        String lockId = Integer.toHexString(System.identityHashCode(this));
        consumer.accept(lockId, sequenceNumber, reentriesByThreadIdCopy);
    }

    @Override
    public void close() {
        ThreadLockStats threadLockStats = LockStats.getForCurrentThread();
        // Update metrics now before release() to avoid double-counting time in locked state.
        invoke(-1L, threadLockStats::preRelease);
        try {
            mutex.release();
            threadLockStats.postRelease();
        }
        catch (Exception e) {
            threadLockStats.releaseFailed();
            throw new RuntimeException("Exception releasing lock '" + lockPath + "'");
        }
    }

    protected String lockPath() { return lockPath; }

    @Override
    public String toString() {
        return "Lock{" + lockPath + "}";
    }
}


