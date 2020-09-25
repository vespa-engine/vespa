// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import com.yahoo.vespa.curator.Lock;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
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

    private static final int MAX_COMPLETED_LOCK_INFOS_SIZE = 5;
    /** Would have used a thread-safe priority queue. */
    private static final Object completedLockInfosMonitor = new Object();
    private static final PriorityQueue<LockInfo> completedLockInfos =
            new PriorityQueue<>(Comparator.comparing(LockInfo::getDurationInTerminalStateAndForPriorityQueue));

    private static final ConcurrentHashMap<String, LockCounters> countersByLockPath = new ConcurrentHashMap<>();

    private final Thread thread;
    private final String lockPath;
    private final ReentrantLock lock;
    private final LockCounters lockCountersForPath;

    /** The locks are reentrant so there may be more than 1 lock for this thread. */
    private final ConcurrentLinkedQueue<LockInfo> lockInfos = new ConcurrentLinkedQueue<>();

    public static Map<String, LockCounters> getLockCountersByPath() { return Map.copyOf(countersByLockPath); }

    public static List<ThreadLockInfo> getThreadLockInfos() { return List.copyOf(locks.values()); }

    public static List<LockInfo> getSlowLockInfos() {
        synchronized (completedLockInfosMonitor) {
            return List.copyOf(completedLockInfos);
        }
    }

    /** Returns the per-thread singleton ThreadLockInfo. */
    public static ThreadLockInfo getCurrentThreadLockInfo(String lockPath, ReentrantLock lock) {
        return locks.computeIfAbsent(
                Thread.currentThread(),
                currentThread -> {
                    LockCounters lockCounters = countersByLockPath.computeIfAbsent(lockPath, ignored -> new LockCounters());
                    return new ThreadLockInfo(currentThread, lockPath, lock, lockCounters);
                });
    }

    ThreadLockInfo(Thread currentThread, String lockPath, ReentrantLock lock, LockCounters lockCountersForPath) {
        this.thread = currentThread;
        this.lockPath = lockPath;
        this.lock = lock;
        this.lockCountersForPath = lockCountersForPath;
    }

    public String getThreadName() { return thread.getName(); }
    public String getLockPath() { return lockPath; }
    public List<LockInfo> getLockInfos() { return List.copyOf(lockInfos); }

    /** Mutable method (see class doc) */
    public void invokingAcquire(Duration timeout) {
        lockCountersForPath.invokeAcquireCount.incrementAndGet();
        lockCountersForPath.inCriticalRegionCount.incrementAndGet();
        lockInfos.add(LockInfo.invokingAcquire(thread, lockPath, lock.getHoldCount(), timeout));
    }

    /** Mutable method (see class doc) */
    public void acquireTimedOut() {
        if (lockInfos.size() > 1) {
            lockCountersForPath.timeoutOnReentrancyErrorCount.incrementAndGet();
        }

        removeLastLockInfo(lockCountersForPath.timeoutOnReentrancyErrorCount, LockInfo::timedOut);
    }

    /** Mutable method (see class doc) */
    public void lockAcquired() {
        lockCountersForPath.lockAcquiredCount.incrementAndGet();

        getLastLockInfo().ifPresent(LockInfo::lockAcquired);
    }

    /** Mutable method (see class doc) */
    public void failedToAcquireReentrantLock() {
        removeLastLockInfo(lockCountersForPath.failedToAcquireReentrantLockCount, LockInfo::failedToAcquireReentrantLock);
    }

    /** Mutable method (see class doc) */
    public void lockReleased() {
        removeLastLockInfo(lockCountersForPath.locksReleasedCount, LockInfo::released);
    }

    private Optional<LockInfo> getLastLockInfo() {
        return lockInfos.isEmpty() ? Optional.empty() : Optional.of(lockInfos.peek());
    }

    private void removeLastLockInfo(AtomicInteger metricToIncrement, Consumer<LockInfo> completeLockInfo) {
        metricToIncrement.incrementAndGet();
        lockCountersForPath.inCriticalRegionCount.decrementAndGet();

        if (lockInfos.isEmpty()) {
            lockCountersForPath.noLocksErrorCount.incrementAndGet();
            return;
        }

        LockInfo lockInfo = lockInfos.poll();
        completeLockInfo.accept(lockInfo);

        synchronized (completedLockInfosMonitor) {
            if (completedLockInfos.size() < MAX_COMPLETED_LOCK_INFOS_SIZE) {
                lockInfo.fillStackTrace();
                completedLockInfos.add(lockInfo);
            } else if (lockInfo.getDurationInTerminalStateAndForPriorityQueue()
                    .compareTo(completedLockInfos.peek().getDurationInTerminalStateAndForPriorityQueue()) > 0) {
                completedLockInfos.poll();
                lockInfo.fillStackTrace();
                completedLockInfos.add(lockInfo);
            }
        }
    }
}
