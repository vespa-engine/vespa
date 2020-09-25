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

    private final Thread thread;
    private final String lockPath;
    private final Instant acquireInstant;
    private final Duration timeout;

    private volatile Optional<Instant> lockAcquiredInstant = Optional.empty();
    private volatile Optional<Instant> terminalStateInstant = Optional.empty();
    private volatile Optional<String> stackTrace = Optional.empty();

    public static LockInfo invokingAcquire(Thread thread, String lockPath, Duration timeout) {
        return new LockInfo(thread, lockPath, timeout);
    }

    public enum LockState {
        ACQUIRING(false), ACQUIRE_FAILED(true), TIMED_OUT(true), ACQUIRED(false), RELEASED(true);

        private final boolean terminal;

        LockState(boolean terminal) { this.terminal = terminal; }

        public boolean isTerminal() { return terminal; }
    }

    private volatile LockState lockState = LockState.ACQUIRING;

    private LockInfo(Thread thread, String lockPath, Duration timeout) {
        this.thread = thread;
        this.lockPath = lockPath;
        this.acquireInstant = Instant.now();
        this.timeout = timeout;
    }

    public String getThreadName() { return thread.getName(); }
    public String getLockPath() { return lockPath; }
    public Instant getTimeAcquiredWasInvoked() { return acquireInstant; }
    public Duration getAcquireTimeout() { return timeout; }
    public LockState getLockState() { return lockState; }
    public Optional<Instant> getTimeLockWasAcquired() { return lockAcquiredInstant; }
    public Optional<Instant> getTimeTerminalStateWasReached() { return terminalStateInstant; }
    public Optional<String> getStackTrace() { return stackTrace; }

    public Duration getDurationOfAcquire() {
        return Duration.between(acquireInstant, lockAcquiredInstant.orElseGet(Instant::now));
    }

    public Duration getDurationWithLock() {
        return lockAcquiredInstant
                .map(start -> Duration.between(start, terminalStateInstant.orElseGet(Instant::now)))
                .orElse(Duration.ZERO);
    }

    public Duration getDuration() { return Duration.between(acquireInstant, terminalStateInstant.orElseGet(Instant::now)); }

    /** Get time from just before trying to acquire lock to the time the terminal state was reached, or ZERO. */
    public Duration getDurationInTerminalStateAndForPriorityQueue() {
        return terminalStateInstant.map(instant -> Duration.between(acquireInstant, instant)).orElse(Duration.ZERO);
    }

    /** Fill in the stack trace starting at the caller's stack frame. */
    public void fillStackTrace() {
        // This method is public. If invoked concurrently, the this.stackTrace may be updated twice,
        // which is fine.

        if (this.stackTrace.isPresent()) return;

        var stackTrace = new StringBuilder();

        StackTraceElement[] elements = thread.getStackTrace();
        // first stack frame is "java.lang.Thread(Thread.java:1606)" or "jdk.internal.misc.Unsafe(Unsafe.java:-2)"!?
        for (int i = 0; i < elements.length; ++i) {
            var element = elements[i];
            stackTrace.append(element.getClassName())
                      .append('(')
                      .append(element.getFileName())
                      .append(':')
                      .append(element.getLineNumber())
                      .append(")\n");
        }

        this.stackTrace = Optional.of(stackTrace.toString());
    }

    void acquireFailed() { setTerminalState(LockState.ACQUIRE_FAILED); }
    void timedOut() { setTerminalState(LockState.TIMED_OUT); }
    void released() { setTerminalState(LockState.RELEASED); }

    void lockAcquired() {
        lockState = LockState.ACQUIRED;
        lockAcquiredInstant = Optional.of(Instant.now());
    }

    void setTerminalState(LockState terminalState) {
        lockState = terminalState;
        terminalStateInstant = Optional.of(Instant.now());
    }
}
