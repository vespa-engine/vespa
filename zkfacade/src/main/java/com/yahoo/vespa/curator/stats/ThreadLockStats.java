// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.curator.Lock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * This class manages thread-specific statistics and information related to acquiring and releasing
 * {@link Lock}.  Instances of this class contain information tied to a specific thread and lock path.
 *
 * <p>Instances of this class are thread-safe as long as foreign threads (!= this.thread) avoid mutable methods.</p>
 *
 * @author hakon
 */
public class ThreadLockStats {

    private final Thread thread;

    /**
     * The locks are reentrant so there may be more than 1 lock for this thread:
     * The first LockAttempt in lockAttemptsStack was the first and top-most lock that was acquired.
     */
    private final ConcurrentLinkedDeque<LockAttempt> lockAttemptsStack = new ConcurrentLinkedDeque<>();

    /** Non-empty if there is an ongoing recording for this thread. */
    private volatile Optional<RecordedLockAttempts> ongoingRecording = Optional.empty();

    ThreadLockStats(Thread currentThread) {
        this.thread = currentThread;
    }

    public String getThreadName() { return thread.getName(); }

    public String getStackTrace() {
        var stackTrace = new StringBuilder();

        StackTraceElement[] elements = thread.getStackTrace();
        for (int i = 0; i < elements.length; ++i) {
            var element = elements[i];
            stackTrace.append(element.getClassName())
                    .append('.')
                    .append(element.getMethodName())
                    .append('(')
                    .append(element.getFileName())
                    .append(':')
                    .append(element.getLineNumber())
                    .append(")\n");
        }

        return stackTrace.toString();
    }

    public List<LockAttempt> getOngoingLockAttempts() { return List.copyOf(lockAttemptsStack); }
    public Optional<LockAttempt> getTopMostOngoingLockAttempt() { return lockAttemptsStack.stream().findFirst(); }
    public Optional<RecordedLockAttempts> getOngoingRecording() { return ongoingRecording; }

    /** Mutable method (see class doc) */
    public void invokingAcquire(String lockPath, Duration timeout,
                                Optional<Metric> metric, Optional<Metric.Context> metricContext) {
        LockCounters lockCounters = getGlobalLockCounters(lockPath);
        lockCounters.invokeAcquireCount.incrementAndGet();
        lockCounters.inCriticalRegionCount.incrementAndGet();
        LockAttempt lockAttempt = LockAttempt.invokingAcquire(this, lockPath, timeout, metric, metricContext);

        LockAttempt lastLockAttempt = lockAttemptsStack.peekLast();
        if (lastLockAttempt == null) {
            ongoingRecording.ifPresent(recording -> recording.addTopLevelLockAttempt(lockAttempt));
        } else {
            lastLockAttempt.addNestedLockAttempt(lockAttempt);
        }
        lockAttemptsStack.addLast(lockAttempt);
    }

    /** Mutable method (see class doc) */
    public void acquireFailed(String lockPath) {
        LockCounters lockCounters = getGlobalLockCounters(lockPath);
        lockCounters.acquireFailedCount.incrementAndGet();
        removeLastLockAttempt(lockCounters, LockAttempt::acquireFailed);
    }

    /** Mutable method (see class doc) */
    public void acquireTimedOut(String lockPath) {
        LockCounters lockCounters = getGlobalLockCounters(lockPath);

        lockCounters.acquireTimedOutCount.incrementAndGet();
        removeLastLockAttempt(lockCounters, LockAttempt::timedOut);
    }

    /** Mutable method (see class doc) */
    public void lockAcquired(String lockPath) {
        getGlobalLockCounters(lockPath).lockAcquiredCount.incrementAndGet();
        LockAttempt lockAttempt = lockAttemptsStack.peekLast();
        if (lockAttempt == null) {
            throw new IllegalStateException("lockAcquired invoked without lockAttempts");
        }
        lockAttempt.lockAcquired();
    }

    /** Mutable method (see class doc) */
    public void lockReleased(String lockPath) {
        LockCounters lockCounters = getGlobalLockCounters(lockPath);
        lockCounters.locksReleasedCount.incrementAndGet();
        removeLastLockAttempt(lockCounters, LockAttempt::released);
    }

    /** Mutable method (see class doc) */
    public void lockReleaseFailed(String lockPath) {
        LockCounters lockCounters = getGlobalLockCounters(lockPath);
        lockCounters.lockReleaseErrorCount.incrementAndGet();
        removeLastLockAttempt(lockCounters, LockAttempt::releasedWithError);
    }

    /** Mutable method (see class doc) */
    public void startRecording(String recordId) {
        ongoingRecording = Optional.of(RecordedLockAttempts.startRecording(recordId));
    }

    /** Mutable method (see class doc) */
    public void stopRecording() {
        if (ongoingRecording.isPresent()) {
            RecordedLockAttempts recording = ongoingRecording.get();
            ongoingRecording = Optional.empty();

            // We'll keep the recordings with the longest durations.
            recording.stopRecording();
            LockStats.getGlobal().reportNewStoppedRecording(recording);
        }
    }

    private LockCounters getGlobalLockCounters(String lockPath) {
        return LockStats.getGlobal().getLockCounters(lockPath);
    }

    private void removeLastLockAttempt(LockCounters lockCounters, Consumer<LockAttempt> completeLockAttempt) {
        lockCounters.inCriticalRegionCount.decrementAndGet();

        if (lockAttemptsStack.isEmpty()) {
            lockCounters.noLocksErrorCount.incrementAndGet();
            return;
        }

        LockAttempt lockAttempt = lockAttemptsStack.pollLast();
        completeLockAttempt.accept(lockAttempt);

        LockStats.getGlobal().maybeSample(lockAttempt);
    }
}
