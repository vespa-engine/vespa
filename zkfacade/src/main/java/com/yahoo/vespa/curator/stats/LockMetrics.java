// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import com.yahoo.vespa.curator.stats.LatencyStats.ActiveInterval;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A collection of counters for events related to lock acquisition and release.
 *
 * @author hakon
 */
public class LockMetrics {
    private final AtomicInteger acquireCount = new AtomicInteger(0);
    private final AtomicInteger acquireFailedCount = new AtomicInteger(0);
    private final AtomicInteger acquireTimedOutCount = new AtomicInteger(0);
    private final AtomicInteger acquireSucceededCount = new AtomicInteger(0);
    private final AtomicInteger releaseCount = new AtomicInteger(0);
    private final AtomicInteger releaseFailedCount = new AtomicInteger(0);
    private final AtomicInteger reentryCount = new AtomicInteger(0);

    private final AtomicInteger cumulativeAcquireCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeAcquireFailedCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeAcquireTimedOutCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeAcquireSucceededCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeReleaseCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeReleaseFailedCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeReentryCount = new AtomicInteger(0);

    private final LatencyStats acquireStats = new LatencyStats();
    private final LatencyStats lockedStats = new LatencyStats();

    private final AtomicInteger deadlockCount = new AtomicInteger(0);
    private final AtomicInteger acquireWithoutReleaseCount = new AtomicInteger(0);
    private final AtomicInteger nakedReleaseCount = new AtomicInteger(0);
    private final AtomicInteger foreignReleaseCount = new AtomicInteger(0);

    private final AtomicInteger cumulativeDeadlockCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeAcquireWithoutReleaseCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeNakedReleaseCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeForeignReleaseCount = new AtomicInteger(0);

    /** Returns a Runnable that must be invoked when the acquire() finishes. */
    ActiveInterval acquireInvoked(boolean reentry) {
        if (reentry) {
            reentryCount.incrementAndGet();
            cumulativeReentryCount.incrementAndGet();
            return () -> { };
        }

        acquireCount.incrementAndGet();
        cumulativeAcquireCount.incrementAndGet();
        return acquireStats.startNewInterval();
    }

    void acquireFailed(boolean reentry, ActiveInterval acquireInterval) {
        acquireInterval.close();
        if (reentry) return;
        acquireFailedCount.incrementAndGet();
        cumulativeAcquireFailedCount.incrementAndGet();
    }

    void acquireTimedOut(boolean reentry, ActiveInterval acquireInterval) {
        acquireInterval.close();
        if (reentry) return;
        acquireTimedOutCount.incrementAndGet();
        cumulativeAcquireTimedOutCount.incrementAndGet();
    }

    ActiveInterval lockAcquired(boolean reentry, ActiveInterval acquireInterval) {
        acquireInterval.close();
        if (reentry) return () -> {};
        acquireSucceededCount.incrementAndGet();
        cumulativeAcquireSucceededCount.incrementAndGet();
        return lockedStats.startNewInterval();
    }

    void preRelease(boolean reentry, ActiveInterval lockedInterval) {
        lockedInterval.close();
        if (reentry) return;
        releaseCount.incrementAndGet();
        cumulativeReleaseCount.incrementAndGet();
    }

    void releaseFailed(boolean reentry) {
        if (reentry) return;
        releaseFailedCount.incrementAndGet();
        cumulativeReleaseFailedCount.incrementAndGet();
    }

    void incrementDeadlockCount() {
        deadlockCount.incrementAndGet();
        cumulativeDeadlockCount.incrementAndGet();
    }

    void incrementAcquireWithoutReleaseCount() {
        acquireWithoutReleaseCount.incrementAndGet();
        cumulativeAcquireWithoutReleaseCount.incrementAndGet();
    }

    void incrementNakedReleaseCount() {
        nakedReleaseCount.incrementAndGet();
        cumulativeNakedReleaseCount.incrementAndGet();
    }

    void incrementForeignReleaseCount() {
        foreignReleaseCount.incrementAndGet();
        cumulativeForeignReleaseCount.incrementAndGet();
    }

    public int getAndResetAcquireCount() { return acquireCount.getAndSet(0); }
    public int getAndResetAcquireFailedCount() { return acquireFailedCount.getAndSet(0); }
    public int getAndResetAcquireTimedOutCount() { return acquireTimedOutCount.getAndSet(0); }
    public int getAndResetAcquireSucceededCount() { return acquireSucceededCount.getAndSet(0); }
    public int getAndResetReleaseCount() { return releaseCount.getAndSet(0); }
    public int getAndResetReleaseFailedCount() { return releaseFailedCount.getAndSet(0); }
    public int getAndResetReentryCount() { return reentryCount.getAndSet(0); }
    public int getAndResetDeadlockCount() { return deadlockCount.getAndSet(0); }
    public int getAndResetAcquireWithoutReleaseCount() { return acquireWithoutReleaseCount.getAndSet(0); }
    public int getAndResetNakedReleaseCount() { return nakedReleaseCount.getAndSet(0); }
    public int getAndResetForeignReleaseCount() { return foreignReleaseCount.getAndSet(0); }

    public int getCumulativeAcquireCount() { return cumulativeAcquireCount.get(); }
    public int getCumulativeAcquireFailedCount() { return cumulativeAcquireFailedCount.get(); }
    public int getCumulativeAcquireTimedOutCount() { return cumulativeAcquireTimedOutCount.get(); }
    public int getCumulativeAcquireSucceededCount() { return cumulativeAcquireSucceededCount.get(); }
    public int getCumulativeReleaseCount() { return cumulativeReleaseCount.get(); }
    public int getCumulativeReleaseFailedCount() { return cumulativeReleaseFailedCount.get(); }
    public int getCumulativeReentryCount() { return cumulativeReentryCount.get(); }
    public int getCumulativeDeadlockCount() { return cumulativeDeadlockCount.get(); }
    public int getCumulativeAcquireWithoutReleaseCount() { return cumulativeAcquireWithoutReleaseCount.get(); }
    public int getCumulativeNakedReleaseCount() { return cumulativeNakedReleaseCount.get(); }
    public int getCumulativeForeignReleaseCount() { return cumulativeForeignReleaseCount.get(); }

    public LatencyMetrics getAcquireLatencyMetrics() { return acquireStats.getLatencyMetrics(); }
    public LatencyMetrics getLockedLatencyMetrics() { return lockedStats.getLatencyMetrics(); }

    public LatencyMetrics getAndResetAcquireLatencyMetrics() { return acquireStats.getLatencyMetricsAndStartNewPeriod(); }
    public LatencyMetrics getAndResetLockedLatencyMetrics() { return lockedStats.getLatencyMetricsAndStartNewPeriod(); }

    //  For tests
    LockMetrics setAcquireCount(int count) { acquireCount.set(count); return this; }
    LockMetrics setAcquireFailedCount(int count) { acquireFailedCount.set(count); return this; }
    LockMetrics setAcquireTimedOutCount(int count) { acquireTimedOutCount.set(count); return this; }
    LockMetrics setAcquireSucceededCount(int count) { acquireSucceededCount.set(count); return this; }
    LockMetrics setReleaseCount(int count) { releaseCount.set(count); return this; }
    LockMetrics setReleaseFailedCount(int count) { releaseFailedCount.set(count); return this; }
    LockMetrics setReentryCount(int count) { reentryCount.set(count); return this; }

    //  For tests
    LockMetrics setCumulativeAcquireCount(int count) { cumulativeAcquireCount.set(count); return this; }
    LockMetrics setCumulativeAcquireFailedCount(int count) { cumulativeAcquireFailedCount.set(count); return this; }
    LockMetrics setCumulativeAcquireTimedOutCount(int count) { cumulativeAcquireTimedOutCount.set(count); return this; }
    LockMetrics setCumulativeAcquireSucceededCount(int count) { cumulativeAcquireSucceededCount.set(count); return this; }
    LockMetrics setCumulativeReleaseCount(int count) { cumulativeReleaseCount.set(count); return this; }
    LockMetrics setCumulativeReleaseFailedCount(int count) { cumulativeReleaseFailedCount.set(count); return this; }
    LockMetrics setCumulativeReentryCount(int count) { cumulativeReentryCount.set(count); return this; }

    @Override
    public String toString() {
        return "LockMetrics{" +
                "acquireCount=" + acquireCount +
                ", acquireFailedCount=" + acquireFailedCount +
                ", acquireTimedOutCount=" + acquireTimedOutCount +
                ", acquireSucceededCount=" + acquireSucceededCount +
                ", releaseCount=" + releaseCount +
                ", releaseFailedCount=" + releaseFailedCount +
                ", reentryCount=" + reentryCount +
                ", cumulativeAcquireCount=" + cumulativeAcquireCount +
                ", cumulativeAcquireFailedCount=" + cumulativeAcquireFailedCount +
                ", cumulativeAcquireTimedOutCount=" + cumulativeAcquireTimedOutCount +
                ", cumulativeAcquireSucceededCount=" + cumulativeAcquireSucceededCount +
                ", cumulativeReleaseCount=" + cumulativeReleaseCount +
                ", cumulativeReleaseFailedCount=" + cumulativeReleaseFailedCount +
                ", cumulativeReentryCount=" + cumulativeReentryCount +
                ", acquireStats=" + acquireStats +
                ", lockedStats=" + lockedStats +
                '}';
    }
}
