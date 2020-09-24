// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * This class contains process-wide statistics and information related to acquiring and releasing
 * {@link Lock}.  Instances of this class contain information tied to a specific thread and lock path.
 *
 * <p>Instances of this class are thread-safe as long as foreign threads (!= this.thread) avoid mutable methods.</p>
 */
public class ThreadLockInfo {

    private static final ConcurrentHashMap<Thread, ThreadLockInfo> locks = new ConcurrentHashMap<>();

    private static final int MAX_COMPLETED_LOCK_INFOS_SIZE = 10;
    private static final ConcurrentLinkedDeque<LockInfo> completedLockInfos = new ConcurrentLinkedDeque<>();

    private static final AtomicInteger invokeAcquireCount = new AtomicInteger(0);
    private static final AtomicInteger inCriticalRegionCount = new AtomicInteger(0);
    private static final AtomicInteger acquireTimedOutCount = new AtomicInteger(0);
    private static final AtomicInteger lockAcquiredCount = new AtomicInteger(0);
    private static final AtomicInteger locksReleasedCount = new AtomicInteger(0);

    private static final AtomicInteger failedToAcquireReentrantLockCount = new AtomicInteger(0);
    private static final AtomicInteger noLocksErrorCount = new AtomicInteger(0);
    private static final AtomicInteger timeoutOnReentrancyErrorCount = new AtomicInteger(0);

    private final Thread thread;
    private final String lockPath;
    private final ReentrantLock lock;

    /** The locks are reentrant so there may be more than 1 lock for this thread. */
    private final ConcurrentLinkedQueue<LockInfo> lockInfos = new ConcurrentLinkedQueue<>();

    public static int invokeAcquireCount() { return invokeAcquireCount.get(); }
    public static int inCriticalRegionCount() { return inCriticalRegionCount.get(); }
    public static int acquireTimedOutCount() { return acquireTimedOutCount.get(); }
    public static int lockAcquiredCount() { return lockAcquiredCount.get(); }
    public static int locksReleasedCount() { return locksReleasedCount.get(); }
    public static int noLocksErrorCount() { return noLocksErrorCount.get(); }
    public static int failedToAcquireReentrantLockCount() { return failedToAcquireReentrantLockCount.get(); }
    public static int timeoutOnReentrancyErrorCount() { return timeoutOnReentrancyErrorCount.get(); }
    public static List<ThreadLockInfo> getThreadLockInfos() { return List.copyOf(locks.values()); }

    /** Returns the per-thread singleton ThreadLockInfo. */
    static ThreadLockInfo getCurrentThreadLockInfo(String lockPath, ReentrantLock lock) {
        return locks.computeIfAbsent(
                Thread.currentThread(),
                currentThread -> new ThreadLockInfo(currentThread, lockPath, lock));
    }

    ThreadLockInfo(Thread currentThread, String lockPath, ReentrantLock lock) {
        this.thread = currentThread;
        this.lockPath = lockPath;
        this.lock = lock;
    }

    public String getThreadName() { return thread.getName(); }
    public String getLockPath() { return lockPath; }
    public List<LockInfo> getLockInfos() { return List.copyOf(lockInfos); }

    /** Mutable method (see class doc) */
    void invokingAcquire(Duration timeout) {
        invokeAcquireCount.incrementAndGet();
        inCriticalRegionCount.incrementAndGet();
        lockInfos.add(LockInfo.invokingAcquire(lock.getHoldCount(), timeout));
    }

    /** Mutable method (see class doc) */
    void acquireTimedOut() {
        if (lockInfos.size() > 1) {
            timeoutOnReentrancyErrorCount.incrementAndGet();
        }

        removeLastLockInfo(acquireTimedOutCount, LockInfo::timedOut);
    }

    /** Mutable method (see class doc) */
    void lockAcquired() {
        lockAcquiredCount.incrementAndGet();
        getLastLockInfo().ifPresent(LockInfo::lockAcquired);
    }

    /** Mutable method (see class doc) */
    void failedToAcquireReentrantLock() {
        removeLastLockInfo(failedToAcquireReentrantLockCount, LockInfo::failedToAcquireReentrantLock);
    }

    /** Mutable method (see class doc) */
    void lockReleased() {
        removeLastLockInfo(locksReleasedCount, LockInfo::released);
    }

    private Optional<LockInfo> getLastLockInfo() {
        return lockInfos.isEmpty() ? Optional.empty() : Optional.of(lockInfos.peek());
    }

    private void removeLastLockInfo(AtomicInteger metricToIncrement, Consumer<LockInfo> completeLockInfo) {
        metricToIncrement.incrementAndGet();
        inCriticalRegionCount.decrementAndGet();

        if (lockInfos.isEmpty()) {
            noLocksErrorCount.incrementAndGet();
            return;
        }

        LockInfo lockInfo = lockInfos.poll();
        completeLockInfo.accept(lockInfo);

        if (completedLockInfos.size() >= MAX_COMPLETED_LOCK_INFOS_SIZE) {
            // This is thread-safe, as no-one but currentThread mutates completedLockInfos
            completedLockInfos.removeLast();
        }
        completedLockInfos.addFirst(lockInfo);
    }
}
