// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A collection of counters for events related to lock acquisition and release.
 *
 * @author hakon
 */
public class LockCounters {
    final AtomicInteger invokeAcquireCount = new AtomicInteger(0);
    final AtomicInteger inCriticalRegionCount = new AtomicInteger(0);
    final AtomicInteger acquireTimedOutCount = new AtomicInteger(0);
    final AtomicInteger lockAcquiredCount = new AtomicInteger(0);
    final AtomicInteger locksReleasedCount = new AtomicInteger(0);

    final AtomicInteger failedToAcquireReentrantLockCount = new AtomicInteger(0);
    final AtomicInteger noLocksErrorCount = new AtomicInteger(0);
    final AtomicInteger timeoutOnReentrancyErrorCount = new AtomicInteger(0);

    public int invokeAcquireCount() { return invokeAcquireCount.get(); }
    public int inCriticalRegionCount() { return inCriticalRegionCount.get(); }
    public int acquireTimedOutCount() { return acquireTimedOutCount.get(); }
    public int lockAcquiredCount() { return lockAcquiredCount.get(); }
    public int locksReleasedCount() { return locksReleasedCount.get(); }
    public int failedToAcquireReentrantLockCount() { return failedToAcquireReentrantLockCount.get(); }
    public int noLocksErrorCount() { return noLocksErrorCount.get(); }
    public int timeoutOnReentrancyErrorCount() { return timeoutOnReentrancyErrorCount.get(); }
}
