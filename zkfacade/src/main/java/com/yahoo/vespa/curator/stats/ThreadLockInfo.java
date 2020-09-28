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
public class ThreadLockInfo {

    private static final ConcurrentHashMap<Thread, ThreadLockInfo> locks = new ConcurrentHashMap<>();

    private static final LockInfoSamples completedLockInfoSamples = new LockInfoSamples();

    private static final ConcurrentHashMap<String, LockCounters> countersByLockPath = new ConcurrentHashMap<>();

    private final Thread thread;

    /** The locks are reentrant so there may be more than 1 lock for this thread. */
    private final ConcurrentLinkedDeque<LockInfo> lockInfos = new ConcurrentLinkedDeque<>();

    public static Map<String, LockCounters> getLockCountersByPath() { return Map.copyOf(countersByLockPath); }

    public static List<ThreadLockInfo> getThreadLockInfos() { return List.copyOf(locks.values()); }

    public static List<LockInfo> getLockInfoSamples() {
        return completedLockInfoSamples.asList();
    }

    /** Returns the per-thread singleton ThreadLockInfo. */
    public static ThreadLockInfo getCurrentThreadLockInfo() {
        return locks.computeIfAbsent(Thread.currentThread(), ThreadLockInfo::new);
    }

    static void clearStaticDataForTesting() {
        locks.clear();
        completedLockInfoSamples.clear();
        countersByLockPath.clear();
    }

    ThreadLockInfo(Thread currentThread) {
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

    public List<LockInfo> getLockInfos() { return List.copyOf(lockInfos); }

    /** Mutable method (see class doc) */
    public void invokingAcquire(String lockPath, Duration timeout) {
        LockCounters lockCounters = getLockCounters(lockPath);
        lockCounters.invokeAcquireCount.incrementAndGet();
        lockCounters.inCriticalRegionCount.incrementAndGet();
        lockInfos.addLast(LockInfo.invokingAcquire(this, lockPath, timeout));
    }

    /** Mutable method (see class doc) */
    public void acquireFailed(String lockPath) {
        LockCounters lockCounters = getLockCounters(lockPath);
        lockCounters.acquireFailedCount.incrementAndGet();
        removeLastLockInfo(lockCounters, LockInfo::acquireFailed);
    }

    /** Mutable method (see class doc) */
    public void acquireTimedOut(String lockPath) {
        LockCounters lockCounters = getLockCounters(lockPath);
        if (lockInfos.size() > 1) {
            lockCounters.timeoutOnReentrancyErrorCount.incrementAndGet();
        }

        lockCounters.acquireTimedOutCount.incrementAndGet();
        removeLastLockInfo(lockCounters, LockInfo::timedOut);
    }

    /** Mutable method (see class doc) */
    public void lockAcquired(String lockPath) {
        getLockCounters(lockPath).lockAcquiredCount.incrementAndGet();
        LockInfo lastLockInfo = lockInfos.peekLast();
        if (lastLockInfo == null) {
            throw new IllegalStateException("lockAcquired invoked without lockInfos");
        }
        lastLockInfo.lockAcquired();
    }

    /** Mutable method (see class doc) */
    public void lockReleased(String lockPath) {
        LockCounters lockCounters = getLockCounters(lockPath);
        lockCounters.locksReleasedCount.incrementAndGet();
        removeLastLockInfo(lockCounters, LockInfo::released);
    }

    private LockCounters getLockCounters(String lockPath) {
        return countersByLockPath.computeIfAbsent(lockPath, __ -> new LockCounters());
    }

    private void removeLastLockInfo(LockCounters lockCounters, Consumer<LockInfo> completeLockInfo) {
        lockCounters.inCriticalRegionCount.decrementAndGet();

        if (lockInfos.isEmpty()) {
            lockCounters.noLocksErrorCount.incrementAndGet();
            return;
        }

        LockInfo lockInfo = lockInfos.pollLast();
        completeLockInfo.accept(lockInfo);
        completedLockInfoSamples.maybeSample(lockInfo);
    }
}
