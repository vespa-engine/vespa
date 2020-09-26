// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Information about a lock.
 *
 * <p>Should be mutated by a single thread, except {@link #fillStackTrace()} which can be
 * invoked by any threads.  Other threads may see an inconsistent state of this instance.</p>
 *
 * @author hakon
 */
public class LockInfo {

    private final ThreadLockInfo threadLockInfo;
    private final String lockPath;
    private final Instant callAcquireInstant;
    private final Duration timeout;

    private volatile Optional<Instant> lockAcquiredInstant = Optional.empty();
    private volatile Optional<Instant> terminalStateInstant = Optional.empty();
    private volatile Optional<String> stackTrace = Optional.empty();

    public static LockInfo invokingAcquire(ThreadLockInfo threadLockInfo, String lockPath, Duration timeout) {
        return new LockInfo(threadLockInfo, lockPath, timeout, Instant.now());
    }

    public enum LockState {
        ACQUIRING(false), ACQUIRE_FAILED(true), TIMED_OUT(true), ACQUIRED(false), RELEASED(true);

        private final boolean terminal;

        LockState(boolean terminal) { this.terminal = terminal; }

        public boolean isTerminal() { return terminal; }
    }

    private volatile LockState lockState = LockState.ACQUIRING;

    private LockInfo(ThreadLockInfo threadLockInfo, String lockPath, Duration timeout, Instant callAcquireInstant) {
        this.threadLockInfo = threadLockInfo;
        this.lockPath = lockPath;
        this.callAcquireInstant = callAcquireInstant;
        this.timeout = timeout;
    }

    public String getThreadName() { return threadLockInfo.getThreadName(); }
    public String getLockPath() { return lockPath; }
    public Instant getTimeAcquiredWasInvoked() { return callAcquireInstant; }
    public Duration getAcquireTimeout() { return timeout; }
    public LockState getLockState() { return lockState; }
    public Optional<Instant> getTimeLockWasAcquired() { return lockAcquiredInstant; }
    public Optional<Instant> getTimeTerminalStateWasReached() { return terminalStateInstant; }
    public Optional<String> getStackTrace() { return stackTrace; }

    public Duration getDurationOfAcquire() {
        return Duration.between(callAcquireInstant, lockAcquiredInstant.orElseGet(Instant::now));
    }

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

        this.stackTrace = Optional.of(threadLockInfo.getStackTrace());
    }

    void acquireFailed() { setTerminalState(LockState.ACQUIRE_FAILED); }
    void timedOut() { setTerminalState(LockState.TIMED_OUT); }
    void released() { setTerminalState(LockState.RELEASED); }

    void lockAcquired() {
        lockState = LockState.ACQUIRED;
        lockAcquiredInstant = Optional.of(Instant.now());
    }

    void setTerminalState(LockState terminalState) { setTerminalState(terminalState, Instant.now()); }

    void setTerminalState(LockState terminalState, Instant instant) {
        lockState = terminalState;
        terminalStateInstant = Optional.of(instant);
    }
}
