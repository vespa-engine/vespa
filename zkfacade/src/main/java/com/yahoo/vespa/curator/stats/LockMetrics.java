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

    private final AtomicInteger cumulativeAcquireCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeAcquireFailedCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeAcquireTimedOutCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeAcquireSucceededCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeReleaseCount = new AtomicInteger(0);
    private final AtomicInteger cumulativeReleaseFailedCount = new AtomicInteger(0);

    private final LatencyStats acquireStats = new LatencyStats();
    private final LatencyStats lockedStats = new LatencyStats();

    /** Returns a Runnable that must be invoked when the acquire() finishes. */
    ActiveInterval acquireInvoked() {
        acquireCount.incrementAndGet();
        cumulativeAcquireCount.incrementAndGet();
        return acquireStats.startNewInterval();
    }

    void acquireFailed(ActiveInterval acquireInterval) {
        acquireInterval.close();
        acquireFailedCount.incrementAndGet();
        cumulativeAcquireFailedCount.incrementAndGet();
    }

    void acquireTimedOut(ActiveInterval acquireInterval) {
        acquireInterval.close();
        acquireTimedOutCount.incrementAndGet();
        cumulativeAcquireTimedOutCount.incrementAndGet();
    }

    ActiveInterval lockAcquired(ActiveInterval acquireInterval) {
        acquireInterval.close();
        acquireSucceededCount.incrementAndGet();
        cumulativeAcquireSucceededCount.incrementAndGet();
        return lockedStats.startNewInterval();
    }

    void preRelease(ActiveInterval lockedInterval) {
        lockedInterval.close();
        releaseCount.incrementAndGet();
        cumulativeReleaseCount.incrementAndGet();
    }

    void releaseFailed() {
        releaseFailedCount.incrementAndGet();
        cumulativeReleaseFailedCount.incrementAndGet();
    }

    public int getAndResetAcquireCount() { return acquireCount.getAndSet(0); }
    public int getAndResetAcquireFailedCount() { return acquireFailedCount.getAndSet(0); }
    public int getAndResetAcquireTimedOutCount() { return acquireTimedOutCount.getAndSet(0); }
    public int getAndResetAcquireSucceededCount() { return acquireSucceededCount.getAndSet(0); }
    public int getAndResetReleaseCount() { return releaseCount.getAndSet(0); }
    public int getAndResetReleaseFailedCount() { return releaseFailedCount.getAndSet(0); }

    public int getCumulativeAcquireCount() { return cumulativeAcquireCount.get(); }
    public int getCumulativeAcquireFailedCount() { return cumulativeAcquireFailedCount.get(); }
    public int getCumulativeAcquireTimedOutCount() { return cumulativeAcquireTimedOutCount.get(); }
    public int getCumulativeAcquireSucceededCount() { return cumulativeAcquireSucceededCount.get(); }
    public int getCumulativeReleaseCount() { return cumulativeReleaseCount.get(); }
    public int getCumulativeReleaseFailedCount() { return cumulativeReleaseFailedCount.get(); }

    public LatencyMetrics getAcquireLatencyMetrics() { return acquireStats.getLatencyMetrics(); }
    public LatencyMetrics getLockedLatencyMetrics() { return lockedStats.getLatencyMetrics(); }

    public LatencyMetrics getAndResetAcquireLatencyMetrics() { return acquireStats.getLatencyMetricsAndStartNewPeriod(); }
    public LatencyMetrics getAndResetLockedLatencyMetrics() { return lockedStats.getLatencyMetricsAndStartNewPeriod(); }

    //  For tests
    void setAcquireCount(int count) { acquireCount.set(count); }
    void setAcquireFailedCount(int count) { acquireFailedCount.set(count); }
    void setAcquireTimedOutCount(int count) { acquireTimedOutCount.set(count); }
    void setAcquireSucceededCount(int count) { acquireSucceededCount.set(count); }
    void setReleaseCount(int count) { releaseCount.set(count); }
    void setReleaseFailedCount(int count) { releaseFailedCount.set(count); }

    //  For tests
    void setCumulativeAcquireCount(int count) { cumulativeAcquireCount.set(count); }
    void setCumulativeAcquireFailedCount(int count) { cumulativeAcquireFailedCount.set(count); }
    void setCumulativeAcquireTimedOutCount(int count) { cumulativeAcquireTimedOutCount.set(count); }
    void setCumulativeAcquireSucceededCount(int count) { cumulativeAcquireSucceededCount.set(count); }
    void setCumulativeReleaseCount(int count) { cumulativeReleaseCount.set(count); }
    void setCumulativeReleaseFailedCount(int count) { cumulativeReleaseFailedCount.set(count); }

    @Override
    public String toString() {
        return "LockMetrics{" +
                "acquireCount=" + acquireCount +
                ", acquireFailedCount=" + acquireFailedCount +
                ", acquireTimedOutCount=" + acquireTimedOutCount +
                ", acquireSucceededCount=" + acquireSucceededCount +
                ", releaseCount=" + releaseCount +
                ", releaseFailedCount=" + releaseFailedCount +
                ", cumulativeAcquireCount=" + cumulativeAcquireCount +
                ", cumulativeAcquireFailedCount=" + cumulativeAcquireFailedCount +
                ", cumulativeAcquireTimedOutCount=" + cumulativeAcquireTimedOutCount +
                ", cumulativeAcquireSucceededCount=" + cumulativeAcquireSucceededCount +
                ", cumulativeReleaseCount=" + cumulativeReleaseCount +
                ", cumulativeReleaseFailedCount=" + cumulativeReleaseFailedCount +
                ", acquireStats=" + acquireStats +
                ", lockedStats=" + lockedStats +
                '}';
    }
}
