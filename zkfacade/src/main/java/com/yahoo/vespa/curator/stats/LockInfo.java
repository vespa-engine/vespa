// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Information about a lock.
 *
 * <p>Should be mutated by a single thread.  Other threads may see an inconsistent state of this instance.</p>
 *
 * @author hakon
 */
public class LockInfo {

    private final int threadHoldCountOnAcquire;
    private final Instant acquireInstant;
    private final Duration timeout;

    private volatile Optional<Instant> lockAcquiredInstant = Optional.empty();
    private volatile Optional<Instant> terminalStateInstant = Optional.empty();

    public static LockInfo invokingAcquire(int holdCount, Duration timeout) {
        return new LockInfo(holdCount, timeout);
    }

    public enum LockState {
        ACQUIRING(false), TIMED_OUT(true), ACQUIRED(false), FAILED_TO_REENTER(true), RELEASED(true);

        private final boolean terminal;

        LockState(boolean terminal) { this.terminal = terminal; }

        public boolean isTerminal() { return terminal; }
    }

    private volatile LockState lockState = LockState.ACQUIRING;

    private LockInfo(int threadHoldCountOnAcquire, Duration timeout) {
        this.threadHoldCountOnAcquire = threadHoldCountOnAcquire;
        this.acquireInstant = Instant.now();
        this.timeout = timeout;
    }

    public int getThreadHoldCountOnAcquire() { return threadHoldCountOnAcquire; }
    public Instant getTimeAcquiredWasInvoked() { return acquireInstant; }
    public Duration getAcquireTimeout() { return timeout; }
    public LockState getLockState() { return lockState; }
    public Optional<Instant> getTimeLockWasAcquired() { return lockAcquiredInstant; }
    public Optional<Instant> getTimeTerminalStateWasReached() { return terminalStateInstant; }

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
}
