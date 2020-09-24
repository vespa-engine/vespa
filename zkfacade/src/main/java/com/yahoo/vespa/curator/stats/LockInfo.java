// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Information about a lock.
 *
 * <p>Should be mutated by a single thread.  Other threads may see an inconsistent state of this instance.</p>
 *
 * @author hakon
 */
public class LockInfo {

    private final String threadName;
    private final String lockPath;
    private final int threadHoldCountOnAcquire;
    private final Instant acquireInstant;
    private final Duration timeout;

    private volatile Optional<Instant> lockAcquiredInstant = Optional.empty();
    private volatile Optional<Instant> terminalStateInstant = Optional.empty();
    private volatile Optional<String> stackTrace = Optional.empty();

    public static LockInfo invokingAcquire(String threadName, String lockPath, int holdCount, Duration timeout) {
        return new LockInfo(threadName, lockPath, holdCount, timeout);
    }

    public enum LockState {
        ACQUIRING(false), TIMED_OUT(true), ACQUIRED(false), FAILED_TO_REENTER(true), RELEASED(true);

        private final boolean terminal;

        LockState(boolean terminal) { this.terminal = terminal; }

        public boolean isTerminal() { return terminal; }
    }

    private volatile LockState lockState = LockState.ACQUIRING;

    private LockInfo(String threadName, String lockPath, int threadHoldCountOnAcquire, Duration timeout) {
        this.threadName = threadName;
        this.lockPath = lockPath;
        this.threadHoldCountOnAcquire = threadHoldCountOnAcquire;
        this.acquireInstant = Instant.now();
        this.timeout = timeout;
    }

    public String getThreadName() { return threadName; }
    public String getLockPath() { return lockPath; }
    public int getThreadHoldCountOnAcquire() { return threadHoldCountOnAcquire; }
    public Instant getTimeAcquiredWasInvoked() { return acquireInstant; }
    public Duration getAcquireTimeout() { return timeout; }
    public LockState getLockState() { return lockState; }
    public Optional<Instant> getTimeLockWasAcquired() { return lockAcquiredInstant; }
    public Optional<Instant> getTimeTerminalStateWasReached() { return terminalStateInstant; }
    public Optional<String> getStackTrace() { return stackTrace; }

    /** Get time from just before trying to acquire lock to the time the terminal state was reached, or ZERO. */
    public Duration getTotalTime() {
        return terminalStateInstant.map(instant -> Duration.between(acquireInstant, instant)).orElse(Duration.ZERO);
    }

    void timedOut() { setTerminalState(LockState.TIMED_OUT); }
    void failedToAcquireReentrantLock() { setTerminalState(LockState.FAILED_TO_REENTER); }
    void released() { setTerminalState(LockState.RELEASED); }

    void lockAcquired() {
        lockState = LockState.ACQUIRED;
        lockAcquiredInstant = Optional.of(Instant.now());
    }

    void setTerminalState(LockState terminalState) {
        lockState = terminalState;
        terminalStateInstant = Optional.of(Instant.now());
    }

    /** Fill in the stack trace starting at the caller's stack frame. */
    void fillStackTrace() {
        final int elementsToIgnore = 1;

        var stackTrace = new StringBuilder();

        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        for (int i = elementsToIgnore; i < elements.length; ++i) {
            Stream.of(elements).forEach(element ->
                    stackTrace.append(element.getClassName())
                            .append('(')
                            .append(element.getFileName())
                            .append(':')
                            .append(element.getLineNumber())
                            .append(")\n"));
        }

        this.stackTrace = Optional.of(stackTrace.toString());
    }
}
