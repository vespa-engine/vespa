// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Collection containing "interesting" {@code LockAttempt}s.
 *
 * @author hakon
 */
// @ThreadSafe
public class LockAttemptSamples {
    private final int maxSamples;

    /** Ensure atomic operations on this collection. */
    private final Object monitor = new Object();

    /** Keep at most one sample for each lock path. */
    private final Map<String, LockAttempt> byLockPath;

    /**
     * Priority queue containing all samples.  The head of this queue (peek()/poll())
     * returns the LockAttempt with the smallest duration.
     */
    private final PriorityQueue<LockAttempt> priorityQueue =
            new PriorityQueue<>(Comparator.comparing(LockAttempt::getStableTotalDuration));

    LockAttemptSamples(int maxSamples) {
        this.maxSamples = maxSamples;
        this.byLockPath = new HashMap<>(maxSamples);
    }

    int size() { return byLockPath.size(); }

    boolean maybeSample(LockAttempt lockAttempt) {
        final boolean added;
        synchronized (monitor) {
            if (shouldAdd(lockAttempt)) {
                byLockPath.put(lockAttempt.getLockPath(), lockAttempt);
                priorityQueue.add(lockAttempt);
                added = true;
            } else {
                added = false;
            }
        }

        if (added) {
            // Unnecessary to invoke under synchronized, although it means that some samples
            // may be without stack trace (just retry if that happens).
            lockAttempt.fillStackTrace();
        }

        return added;
    }

    private boolean shouldAdd(LockAttempt lockAttempt) {
        LockAttempt existingLockAttempt = byLockPath.get(lockAttempt.getLockPath());
        if (existingLockAttempt != null) {
            if (hasLongerDurationThan(lockAttempt, existingLockAttempt)) {
                byLockPath.remove(existingLockAttempt.getLockPath());
                priorityQueue.remove(existingLockAttempt);
                return true;
            }

            return false;
        }

        if (size() < maxSamples) {
            return true;
        }

        // peek() and poll() retrieves the smallest element.
        existingLockAttempt = priorityQueue.peek();  // cannot be null
        if (hasLongerDurationThan(lockAttempt, existingLockAttempt)) {
            priorityQueue.poll();
            byLockPath.remove(existingLockAttempt.getLockPath());
            return true;
        }

        return false;
    }

    List<LockAttempt> asList() {
        synchronized (monitor) {
            return List.copyOf(byLockPath.values());
        }
    }

    private static boolean hasLongerDurationThan(LockAttempt lockAttempt, LockAttempt otherLockAttempt) {
        // Use stable total duration to avoid messing up priority queue.
        return lockAttempt.getStableTotalDuration().compareTo(otherLockAttempt.getStableTotalDuration()) > 0;
    }
}
