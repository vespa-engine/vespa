// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Information about a lock.
 *
 * <p>Should be mutated by a single thread, except {@link #fillStackTrace()} which can be
 * invoked by any threads.  Other threads may see an inconsistent state of this instance.</p>
 *
 * @author hakon
 */
public class LockAttempt {

    private final ThreadLockStats threadLockStats;
    private final String lockPath;
    private final Instant callAcquireInstant;
    private final Duration timeout;
    private final boolean reentry;
    private final LockMetrics lockMetrics;
    private final List<LockAttempt> nestedLockAttempts = new ArrayList<>();
    private final LatencyStats.ActiveInterval activeAcquireInterval;
    // Only accessed by mutating thread:
    private Optional<LatencyStats.ActiveInterval> activeLockedInterval = Optional.empty();

    private volatile Optional<Instant> lockAcquiredInstant = Optional.empty();
    private volatile Optional<Instant> terminalStateInstant = Optional.empty();
    private volatile Optional<String> stackTrace = Optional.empty();

    public enum LockState {
        ACQUIRING(false), ACQUIRE_FAILED(true), TIMED_OUT(true), ACQUIRED(false), RELEASED(true),
        RELEASED_WITH_ERROR(true);

        private final boolean terminal;

        LockState(boolean terminal) { this.terminal = terminal; }

        public boolean isTerminal() { return terminal; }
    }

    private volatile LockState lockState = LockState.ACQUIRING;

    public static LockAttempt invokingAcquire(ThreadLockStats threadLockStats, String lockPath,
                                              Duration timeout, LockMetrics lockMetrics,
                                              boolean reentry) {
        return new LockAttempt(threadLockStats, lockPath, timeout, Instant.now(), lockMetrics, reentry);
    }

    private LockAttempt(ThreadLockStats threadLockStats, String lockPath, Duration timeout,
                        Instant callAcquireInstant, LockMetrics lockMetrics, boolean reentry) {
        this.threadLockStats = threadLockStats;
        this.lockPath = lockPath;
        this.callAcquireInstant = callAcquireInstant;
        this.timeout = timeout;
        this.lockMetrics = lockMetrics;
        this.reentry = reentry;
        this.activeAcquireInterval = lockMetrics.acquireInvoked(reentry);
    }

    public String getThreadName() { return threadLockStats.getThreadName(); }
    public String getLockPath() { return lockPath; }
    public Instant getTimeAcquiredWasInvoked() { return callAcquireInstant; }
    public Duration getAcquireTimeout() { return timeout; }
    public boolean getReentry() { return reentry; }
    public LockState getLockState() { return lockState; }
    public Optional<Instant> getTimeLockWasAcquired() { return lockAcquiredInstant; }
    public boolean isAcquiring() { return lockAcquiredInstant.isEmpty(); }
    public Instant getTimeAcquireEndedOrNow() {
        return lockAcquiredInstant.orElseGet(() -> getTimeTerminalStateWasReached().orElseGet(Instant::now));
    }
    public Optional<Instant> getTimeTerminalStateWasReached() { return terminalStateInstant; }
    public Optional<String> getStackTrace() { return stackTrace; }
    public List<LockAttempt> getNestedLockAttempts() { return List.copyOf(nestedLockAttempts); }

    public Duration getDurationOfAcquire() { return Duration.between(callAcquireInstant, getTimeAcquireEndedOrNow()); }

    public Duration getDurationWithLock() {
        return lockAcquiredInstant
                .map(start -> Duration.between(start, terminalStateInstant.orElseGet(Instant::now)))
                .orElse(Duration.ZERO);
    }

    public Duration getDuration() { return Duration.between(callAcquireInstant, terminalStateInstant.orElseGet(Instant::now)); }

    /** Get time from just before trying to acquire lock to the time the terminal state was reached, or ZERO. */
    public Duration getStableTotalDuration() {
        return terminalStateInstant.map(instant -> Duration.between(callAcquireInstant, instant)).orElse(Duration.ZERO);
    }

    /** Fill in the stack trace starting at the caller's stack frame. */
    public void fillStackTrace() {
        // This method is public. If invoked concurrently, the this.stackTrace may be updated twice,
        // which is fine.

        this.stackTrace = Optional.of(threadLockStats.getStackTrace());
    }

    void addNestedLockAttempt(LockAttempt nestedLockAttempt) {
        nestedLockAttempts.add(nestedLockAttempt);
    }

    void acquireFailed() {
        setTerminalState(LockState.ACQUIRE_FAILED);
        lockMetrics.acquireFailed(reentry, activeAcquireInterval);
    }

    void timedOut() {
        setTerminalState(LockState.TIMED_OUT);
        lockMetrics.acquireTimedOut(reentry, activeAcquireInterval);
    }

    void lockAcquired() {
        lockState = LockState.ACQUIRED;
        lockAcquiredInstant = Optional.of(Instant.now());
        activeLockedInterval = Optional.of(lockMetrics.lockAcquired(reentry, activeAcquireInterval));
    }

    void preRelease() {
        lockMetrics.preRelease(reentry, activeLockedInterval.orElseThrow());
    }

    void postRelease() {
        setTerminalState(LockState.RELEASED);
    }

    void releaseFailed() {
        setTerminalState(LockState.RELEASED_WITH_ERROR);
        lockMetrics.releaseFailed(reentry);
    }

    void setTerminalState(LockState terminalState) { setTerminalState(terminalState, Instant.now()); }

    void setTerminalState(LockState terminalState, Instant instant) {
        lockState = terminalState;
        terminalStateInstant = Optional.of(instant);
    }
}
