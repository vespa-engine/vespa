// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
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

    private final AtomicInteger acquiringNow = new AtomicInteger(0);
    private final AtomicInteger lockedNow = new AtomicInteger(0);

    private final LatencyStore acquireLatencyStore = new LatencyStore();
    private final LatencyStore lockedLatencyStore = new LatencyStore();

    void acquireInvoked() {
        acquireCount.incrementAndGet();
        cumulativeAcquireCount.incrementAndGet();
        acquiringNow.incrementAndGet();
    }

    void acquireFailed(Duration acquireLatency) {
        acquiringNow.decrementAndGet();
        acquireFailedCount.incrementAndGet();
        cumulativeAcquireFailedCount.incrementAndGet();
        acquireLatencyStore.reportLatency(acquireLatency);
    }

    void acquireTimedOut(Duration acquireLatency) {
        acquiringNow.decrementAndGet();
        acquireTimedOutCount.incrementAndGet();
        cumulativeAcquireTimedOutCount.incrementAndGet();
        acquireLatencyStore.reportLatency(acquireLatency);
    }

    void lockAcquired(Duration acquireLatency) {
        acquiringNow.decrementAndGet();
        acquireSucceededCount.incrementAndGet();
        cumulativeAcquireSucceededCount.incrementAndGet();
        acquireLatencyStore.reportLatency(acquireLatency);
        lockedNow.incrementAndGet();
    }

    void release(Duration lockedLatency, Duration totalLatency) {
        lockedNow.decrementAndGet();
        releaseCount.incrementAndGet();
        cumulativeReleaseCount.incrementAndGet();
        lockedLatencyStore.reportLatency(lockedLatency);
    }

    void releaseFailed(Duration lockedLatency, Duration totalLatency) {
        release(lockedLatency, totalLatency);
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

    public int getAcquiringNow() { return acquiringNow.get(); }
    public int getLockedNow() { return lockedNow.get(); }

    public LatencyMetrics getAcquireLatencyMetrics() { return acquireLatencyStore.getLatencyMetrics(); }
    public LatencyMetrics getLockedLatencyMetrics() { return lockedLatencyStore.getLatencyMetrics(); }

    public LatencyMetrics getAndResetAcquireLatencyMetrics() { return acquireLatencyStore.getAndResetLatencyMetrics(); }
    public LatencyMetrics getAndResetLockedLatencyMetrics() { return lockedLatencyStore.getAndResetLatencyMetrics(); }

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

    //  For tests
    void setAcquiringNow(int count) { acquiringNow.set(count); }
    void setLockedNow(int count) { lockedNow.set(count); }

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
                ", acquiringNow=" + acquiringNow +
                ", lockedNow=" + lockedNow +
                ", acquireLatencyStore=" + acquireLatencyStore +
                ", lockedLatencyStore=" + lockedLatencyStore +
                '}';
    }
}
