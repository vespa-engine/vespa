// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import com.yahoo.vespa.curator.Lock;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * This class manages thread-specific statistics and information related to acquiring and releasing
 * {@link Lock}.  Instances of this class contain information tied to a specific thread and lock path.
 *
 * <p>Instances of this class are thread-safe as long as foreign threads (!= this.thread) avoid mutable methods.</p>
 *
 * @author hakon
 */
public class ThreadLockStats {

    private static final Logger logger = Logger.getLogger(ThreadLockStats.class.getName());

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
    public Optional<LockAttempt> getTopMostOngoingLockAttempt() { return Optional.ofNullable(lockAttemptsStack.peekFirst()); }
    /** The most recent and deeply nested ongoing lock attempt. */
    public Optional<LockAttempt> getBottomMostOngoingLockAttempt() { return Optional.ofNullable(lockAttemptsStack.peekLast()); }
    public Optional<RecordedLockAttempts> getOngoingRecording() { return ongoingRecording; }

    /** Mutable method (see class doc) */
    public void invokingAcquire(String lockPath, Duration timeout) {
        boolean reentry = lockAttemptsStack.stream().anyMatch(lockAttempt -> lockAttempt.getLockPath().equals(lockPath));

        if (!reentry) {
            testForDeadlock(lockPath);
        }

        LockAttempt lockAttempt = LockAttempt.invokingAcquire(this, lockPath, timeout,
                getGlobalLockMetrics(lockPath), reentry);

        LockAttempt lastLockAttempt = lockAttemptsStack.peekLast();
        if (lastLockAttempt == null) {
            ongoingRecording.ifPresent(recording -> recording.addTopLevelLockAttempt(lockAttempt));
        } else {
            lastLockAttempt.addNestedLockAttempt(lockAttempt);
        }
        lockAttemptsStack.addLast(lockAttempt);
    }

    /** Mutable method (see class doc) */
    public void acquireFailed() {
        removeLastLockAttempt(LockAttempt::acquireFailed);
    }

    /** Mutable method (see class doc) */
    public void acquireTimedOut() {
        removeLastLockAttempt(LockAttempt::timedOut);
    }

    /** Mutable method (see class doc) */
    public void lockAcquired() {
        withLastLockAttempt(lockAttempt -> {
            // Note on the order of lockAcquired() vs notifyOfThreadHoldingLock(): When the latter is
            // invoked, other threads may query e.g. isAcquired() on the lockAttempt, which would
            // return false in a small window if these two statements were reversed.  Not a biggie,
            // but seems better to ensure LockAttempt is updated first.
            lockAttempt.lockAcquired();

            if (!lockAttempt.isReentry()) {
                LockStats.getGlobal().notifyOfThreadHoldingLock(thread, lockAttempt.getLockPath());
            }
        });
    }

    /** Mutable method (see class doc) */
    public void preRelease(String path) {
        withLastLockAttemptFor(path, lockAttempt -> {
            // Note on the order of these two statement: Same concerns apply here as in lockAcquired().

            if (!lockAttempt.isReentry()) {
                LockStats.getGlobal().notifyOfThreadReleasingLock(thread, lockAttempt.getLockPath());
            }

            lockAttempt.preRelease();
        });
    }

    /** Mutable method (see class doc) */
    public void postRelease(String lockPath) {
        removeLastLockAttemptFor(lockPath, LockAttempt::postRelease);
    }

    /** Mutable method (see class doc) */
    public void releaseFailed(String lockPath) {
        removeLastLockAttemptFor(lockPath, LockAttempt::releaseFailed);
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

    /**
     * Tries to detect whether acquiring a given lock path would deadlock.
     *
     * <p>Thread T0 will deadlock if it tries to acquire a lock on a path L1 held by T1,
     * and T1 is waiting on L2 held by T2, and so forth, and TN is waiting on L0 held by T0.</p>
     *
     *
     * <p>Since the underlying data structures are concurrently being modified (as an optimization,
     * no lock is taken for this calculation), a cycle may be detected not involving T0.</p>
     *
     * <p>This method is a best-effort attempt at detecting deadlocks:  A deadlock may in fact be
     * resolved even though this method throws, if e.g. locks are released just after this
     * method.</p>
     */
    private void testForDeadlock(String pathToAcquire) {
        LockStats globalLockStats = LockStats.getGlobal();
        var errorMessage = new StringBuilder().append("Deadlock detected: Thread ").append(thread.getName());

        // The set of all threads waiting.  If we're waiting in a cycle, there is a deadlock...
        Set<Thread> threadsAcquiring = new HashSet<>();
        Thread threadAcquiringLockPath = thread;
        String lockPath = pathToAcquire;

        while (true) {
            Optional<ThreadLockStats> threadLockStats = globalLockStats.getThreadLockStatsHolding(lockPath);
            if (threadLockStats.isEmpty()) {
                return;
            }

            Thread threadHoldingLockPath = threadLockStats.get().thread;
            if (threadAcquiringLockPath == threadHoldingLockPath) {
                // reentry
                return;
            }

            errorMessage.append(", trying to acquire lock ")
                        .append(lockPath)
                        .append(" held by thread ")
                        .append(threadHoldingLockPath.getName());
            if (threadsAcquiring.contains(threadHoldingLockPath)) {
                // deadlock
                getGlobalLockMetrics(pathToAcquire).incrementDeadlockCount();
                logger.warning(errorMessage.toString());
                return;
            }

            Optional<String> nextLockPath = threadLockStats.get().acquiringLockPath();
            if (nextLockPath.isEmpty()) {
                return;
            }

            threadsAcquiring.add(threadAcquiringLockPath);
            lockPath = nextLockPath.get();
            threadAcquiringLockPath = threadHoldingLockPath;
        }
    }

    private LockMetrics getGlobalLockMetrics(String lockPath) {
        return LockStats.getGlobal().getLockMetrics(lockPath);
    }

    private Optional<String> acquiringLockPath() {
        return Optional.ofNullable(lockAttemptsStack.peekLast())
                .filter(LockAttempt::isAcquiring)
                .map(LockAttempt::getLockPath);
    }

    private void withLastLockAttempt(Consumer<LockAttempt> lockAttemptConsumer) {
        LockAttempt lockAttempt = lockAttemptsStack.peekLast();
        if (lockAttempt == null) {
            logger.warning("Unable to get last lock attempt as the lock attempt stack is empty");
            return;
        }

        lockAttemptConsumer.accept(lockAttempt);
    }

    private void removeLastLockAttempt(Consumer<LockAttempt> completeLockAttempt) {
        LockAttempt lockAttempt = lockAttemptsStack.pollLast();
        if (lockAttempt == null) {
            logger.warning("Unable to remove last lock attempt as the lock attempt stack is empty");
            return;
        }

        completeLockAttempt.accept(lockAttempt);

        LockStats.getGlobal().maybeSample(lockAttempt);
    }

    private void withLastLockAttemptFor(String lockPath, Consumer<LockAttempt> consumer) {
        Iterator<LockAttempt> lockAttemptIterator = lockAttemptsStack.descendingIterator();
        while (lockAttemptIterator.hasNext()) {
            LockAttempt lockAttempt = lockAttemptIterator.next();
            if (lockAttempt.getLockPath().equals(lockPath)) {
                consumer.accept(lockAttempt);
                return;
            }
        }

        logger.warning("Unable to find any lock attempts for " + lockPath);
    }

    private void removeLastLockAttemptFor(String lockPath, Consumer<LockAttempt> consumer) {
        Iterator<LockAttempt> lockAttemptIterator = lockAttemptsStack.descendingIterator();
        while (lockAttemptIterator.hasNext()) {
            LockAttempt lockAttempt = lockAttemptIterator.next();
            if (lockAttempt.getLockPath().equals(lockPath)) {
                lockAttemptIterator.remove();
                consumer.accept(lockAttempt);
                LockStats.getGlobal().maybeSample(lockAttempt);
                return;
            }
        }

        logger.warning("Unable to remove last lock attempt as no locks were found for " + lockPath);
    }
}
