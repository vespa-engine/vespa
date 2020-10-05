// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class manages statistics related to lock attempts on {@link com.yahoo.vespa.curator.Lock}.
 *
 * @author hakon
 */
public class LockStats {
    // No 'volatile' is needed because field is only ever changed for testing which is single-threaded.
    private static LockStats stats = new LockStats();

    private final ConcurrentHashMap<Thread, ThreadLockStats> statsByThread = new ConcurrentHashMap<>();

    private final LockAttemptSamples completedLockAttemptSamples = new LockAttemptSamples(3);

    // Keep recordings in a priority queue, with the smallest element having the smallest duration.
    // Recordings can be large, so keep the number of recordings low.
    private static final int MAX_RECORDINGS = 3;
    private final Object interestingRecordingsMonitor = new Object();
    private final PriorityQueue<RecordedLockAttempts> interestingRecordings =
            new PriorityQueue<>(MAX_RECORDINGS, Comparator.comparing(RecordedLockAttempts::duration));

    private final ConcurrentHashMap<String, LockMetrics> metricsByLockPath = new ConcurrentHashMap<>();

    /** Returns global stats. */
    public static LockStats getGlobal() { return stats; }

    /** Returns stats tied to the current thread. */
    public static ThreadLockStats getForCurrentThread() {
        return stats.statsByThread.computeIfAbsent(Thread.currentThread(), ThreadLockStats::new);
    }

    public static void clearForTesting() {
        stats = new LockStats();
    }

    private LockStats() {}

    public Map<String, LockMetrics> getLockMetricsByPath() { return Map.copyOf(metricsByLockPath); }
    public List<ThreadLockStats> getThreadLockStats() { return List.copyOf(statsByThread.values()); }
    public List<LockAttempt> getLockAttemptSamples() { return completedLockAttemptSamples.asList(); }

    public List<RecordedLockAttempts> getHistoricRecordings() {
        synchronized (interestingRecordingsMonitor) {
            return List.copyOf(interestingRecordings);
        }
    }

    LockMetrics getLockMetrics(String lockPath) {
        return metricsByLockPath.computeIfAbsent(lockPath, __ -> new LockMetrics());
    }

    void maybeSample(LockAttempt lockAttempt) {
        completedLockAttemptSamples.maybeSample(lockAttempt);
    }

    void reportNewStoppedRecording(RecordedLockAttempts recording) {
        synchronized (interestingRecordings) {
            if (interestingRecordings.size() < MAX_RECORDINGS) {
                interestingRecordings.add(recording);
            } else if (recording.duration().compareTo(interestingRecordings.peek().duration()) > 0) {
                // peek() retrieves the smallest element according to the PriorityQueue's
                // comparator.

                interestingRecordings.poll();
                interestingRecordings.add(recording);
            }
        }

    }
}
