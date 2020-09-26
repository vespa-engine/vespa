// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Collection containing "interesting" {@code LockInfo}s.
 *
 * @author hakon
 */
// @ThreadSafe
public class LockInfoSamples {
    private final int maxSamples;

    /** Ensure atomic operations on this collection. */
    private final Object monitor = new Object();

    /** Keep at most one sample for each lock path. */
    private final Map<String, LockInfo> byLockPath;

    /**
     * Priority queue containing all samples.  The head of this queue (peek()/poll())
     * returns the LockInfo with the smallest duration.
     */
    private final PriorityQueue<LockInfo> priorityQueue =
            new PriorityQueue<>(Comparator.comparing(LockInfo::getStableTotalDuration));

    LockInfoSamples() { this(10); }

    LockInfoSamples(int maxSamples) {
        this.maxSamples = maxSamples;
        this.byLockPath = new HashMap<>(maxSamples);
    }

    int size() { return byLockPath.size(); }

    boolean maybeSample(LockInfo lockInfo) {
        final boolean added;
        synchronized (monitor) {
            if (shouldAdd(lockInfo)) {
                byLockPath.put(lockInfo.getLockPath(), lockInfo);
                priorityQueue.add(lockInfo);
                added = true;
            } else {
                added = false;
            }
        }

        if (added) {
            // Unnecessary to invoke under synchronized, although it means that some samples
            // may be without stack trace (just retry if that happens).
            lockInfo.fillStackTrace();
        }

        return added;
    }

    private boolean shouldAdd(LockInfo lockInfo) {
        LockInfo existingLockInfo = byLockPath.get(lockInfo.getLockPath());
        if (existingLockInfo != null) {
            if (hasLongerDurationThan(lockInfo, existingLockInfo)) {
                byLockPath.remove(existingLockInfo.getLockPath());
                priorityQueue.remove(existingLockInfo);
                return true;
            }

            return false;
        }

        if (size() < maxSamples) {
            return true;
        }

        // peek() and poll() retrieves the smallest element.
        existingLockInfo = priorityQueue.peek();  // cannot be null
        if (hasLongerDurationThan(lockInfo, existingLockInfo)) {
            priorityQueue.poll();
            byLockPath.remove(existingLockInfo.getLockPath());
            return true;
        }

        return false;
    }

    List<LockInfo> asList() {
        synchronized (monitor) {
            return List.copyOf(byLockPath.values());
        }
    }

    void clear() {
        synchronized (monitor) {
            byLockPath.clear();
            priorityQueue.clear();
        }
    }

    private static boolean hasLongerDurationThan(LockInfo lockInfo, LockInfo otherLockInfo) {
        // Use stable total duration to avoid messing up priority queue.
        return lockInfo.getStableTotalDuration().compareTo(otherLockInfo.getStableTotalDuration()) > 0;
    }
}
