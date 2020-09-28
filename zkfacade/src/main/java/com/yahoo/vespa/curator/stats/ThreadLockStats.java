// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import com.yahoo.vespa.curator.Lock;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * This class contains process-wide statistics and information related to acquiring and releasing
 * {@link Lock}.  Instances of this class contain information tied to a specific thread and lock path.
 *
 * <p>Instances of this class are thread-safe as long as foreign threads (!= this.thread) avoid mutable methods.</p>
 *
 * @author hakon
 */
public class ThreadLockStats {

    private static final ConcurrentHashMap<Thread, ThreadLockStats> locks = new ConcurrentHashMap<>();

    private static final LockAttemptSamples COMPLETED_LOCK_ATTEMPT_SAMPLES = new LockAttemptSamples();

    private static final ConcurrentHashMap<String, LockCounters> countersByLockPath = new ConcurrentHashMap<>();

    private final Thread thread;

    /** The locks are reentrant so there may be more than 1 lock for this thread. */
    private final ConcurrentLinkedDeque<LockAttempt> lockAttempts = new ConcurrentLinkedDeque<>();

    public static Map<String, LockCounters> getLockCountersByPath() { return Map.copyOf(countersByLockPath); }

    public static List<ThreadLockStats> getThreadLockStats() { return List.copyOf(locks.values()); }

    public static List<LockAttempt> getLockAttemptSamples() {
        return COMPLETED_LOCK_ATTEMPT_SAMPLES.asList();
    }

    /** Returns the per-thread singleton ThreadLockStats. */
    public static ThreadLockStats getCurrentThreadLockStats() {
        return locks.computeIfAbsent(Thread.currentThread(), ThreadLockStats::new);
    }

    static void clearStaticDataForTesting() {
        locks.clear();
        COMPLETED_LOCK_ATTEMPT_SAMPLES.clear();
        countersByLockPath.clear();
    }

    ThreadLockStats(Thread currentThread) {
        this.thread = currentThread;
    }

    public String getThreadName() { return thread.getName(); }

    public String getStackTrace() {
        var stackTrace = new StringBuilder();

        StackTraceElement[] elements = thread.getStackTrace();
        for (int i = 0; i < elements.length; ++i) {
            var element = elements[i];
            stackTrace.append(element.getClassName())
                    .append('.')
                    .append(element.getMethodName())
                    .append('(')
                    .append(element.getFileName())
                    .append(':')
                    .append(element.getLineNumber())
                    .append(")\n");
        }

        return stackTrace.toString();
    }

    public List<LockAttempt> getLockAttempts() { return List.copyOf(lockAttempts); }

    /** Mutable method (see class doc) */
    public void invokingAcquire(String lockPath, Duration timeout) {
        LockCounters lockCounters = getLockCounters(lockPath);
        lockCounters.invokeAcquireCount.incrementAndGet();
        lockCounters.inCriticalRegionCount.incrementAndGet();
        lockAttempts.addLast(LockAttempt.invokingAcquire(this, lockPath, timeout));
    }

    /** Mutable method (see class doc) */
    public void acquireFailed(String lockPath) {
        LockCounters lockCounters = getLockCounters(lockPath);
        lockCounters.acquireFailedCount.incrementAndGet();
        removeLastLockAttempt(lockCounters, LockAttempt::acquireFailed);
    }

    /** Mutable method (see class doc) */
    public void acquireTimedOut(String lockPath) {
        LockCounters lockCounters = getLockCounters(lockPath);
        if (lockAttempts.size() > 1) {
            lockCounters.timeoutOnReentrancyErrorCount.incrementAndGet();
        }

        lockCounters.acquireTimedOutCount.incrementAndGet();
        removeLastLockAttempt(lockCounters, LockAttempt::timedOut);
    }

    /** Mutable method (see class doc) */
    public void lockAcquired(String lockPath) {
        getLockCounters(lockPath).lockAcquiredCount.incrementAndGet();
        LockAttempt lastLockAttempt = lockAttempts.peekLast();
        if (lastLockAttempt == null) {
            throw new IllegalStateException("lockAcquired invoked without lockAttempts");
        }
        lastLockAttempt.lockAcquired();
    }

    /** Mutable method (see class doc) */
    public void lockReleased(String lockPath) {
        LockCounters lockCounters = getLockCounters(lockPath);
        lockCounters.locksReleasedCount.incrementAndGet();
        removeLastLockAttempt(lockCounters, LockAttempt::released);
    }

    private LockCounters getLockCounters(String lockPath) {
        return countersByLockPath.computeIfAbsent(lockPath, __ -> new LockCounters());
    }

    private void removeLastLockAttempt(LockCounters lockCounters, Consumer<LockAttempt> completeLockAttempt) {
        lockCounters.inCriticalRegionCount.decrementAndGet();

        if (lockAttempts.isEmpty()) {
            lockCounters.noLocksErrorCount.incrementAndGet();
            return;
        }

        LockAttempt lockAttempt = lockAttempts.pollLast();
        completeLockAttempt.accept(lockAttempt);
        COMPLETED_LOCK_ATTEMPT_SAMPLES.maybeSample(lockAttempt);
    }
}
