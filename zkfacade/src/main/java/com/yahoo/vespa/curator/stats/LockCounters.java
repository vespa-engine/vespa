// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A collection of counters for events related to lock acquisition and release.
 *
 * @author hakon
 */
public class LockCounters {
    final AtomicInteger invokeAcquireCount = new AtomicInteger(0);
    final AtomicInteger inCriticalRegionCount = new AtomicInteger(0);
    final AtomicInteger acquireFailedCount = new AtomicInteger(0);
    final AtomicInteger acquireTimedOutCount = new AtomicInteger(0);
    final AtomicInteger lockAcquiredCount = new AtomicInteger(0);
    final AtomicInteger locksReleasedCount = new AtomicInteger(0);

    final AtomicInteger noLocksErrorCount = new AtomicInteger(0);
    final AtomicInteger timeoutOnReentrancyErrorCount = new AtomicInteger(0);

    public int invokeAcquireCount() { return invokeAcquireCount.get(); }
    public int inCriticalRegionCount() { return inCriticalRegionCount.get(); }
    public int acquireFailedCount() { return acquireFailedCount.get(); }
    public int acquireTimedOutCount() { return acquireTimedOutCount.get(); }
    public int lockAcquiredCount() { return lockAcquiredCount.get(); }
    public int locksReleasedCount() { return locksReleasedCount.get(); }
    public int noLocksErrorCount() { return noLocksErrorCount.get(); }
    public int timeoutOnReentrancyErrorCount() { return timeoutOnReentrancyErrorCount.get(); }

    @Override
    public String toString() {
        return "LockCounters{" +
                "invokeAcquireCount=" + invokeAcquireCount +
                ", inCriticalRegionCount=" + inCriticalRegionCount +
                ", acquireFailedCount=" + acquireFailedCount +
                ", acquireTimedOutCount=" + acquireTimedOutCount +
                ", lockAcquiredCount=" + lockAcquiredCount +
                ", locksReleasedCount=" + locksReleasedCount +
                ", noLocksErrorCount=" + noLocksErrorCount +
                ", timeoutOnReentrancyErrorCount=" + timeoutOnReentrancyErrorCount +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LockCounters that = (LockCounters) o;
        return invokeAcquireCount.get() ==  that.invokeAcquireCount.get() &&
                inCriticalRegionCount.get() == that.inCriticalRegionCount.get() &&
                acquireFailedCount.get() == that.acquireFailedCount.get() &&
                acquireTimedOutCount.get() == that.acquireTimedOutCount.get() &&
                lockAcquiredCount.get() == that.lockAcquiredCount.get() &&
                locksReleasedCount.get() == that.locksReleasedCount.get() &&
                noLocksErrorCount.get() == that.noLocksErrorCount.get() &&
                timeoutOnReentrancyErrorCount.get() == that.timeoutOnReentrancyErrorCount.get();
    }

    @Override
    public int hashCode() {
        return Objects.hash(invokeAcquireCount, inCriticalRegionCount, acquireFailedCount, acquireTimedOutCount, lockAcquiredCount, locksReleasedCount, noLocksErrorCount, timeoutOnReentrancyErrorCount);
    }
}
