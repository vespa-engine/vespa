// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.time.Duration;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/** Read-only observation of one client lifecycle. Waiting never cancels shared shutdown. */
public interface ShutdownHandle {
    enum Phase { OPEN, DRAINING, ABORTING, TERMINATED, FAILED }

    /** Immutable observation; FAILED does not certify that all resources were released. */
    final class Snapshot {
        private final Phase phase;
        private final Throwable failure;
        private final int pendingAdmissions;
        private final int outstandingOperations;

        public Snapshot(Phase phase, Throwable failure, int pendingAdmissions, int outstandingOperations) {
            this.phase = Objects.requireNonNull(phase, "phase");
            if ((phase == Phase.FAILED) != (failure != null))
                throw new IllegalArgumentException("Only FAILED snapshots must contain a failure");
            if (pendingAdmissions < 0 || outstandingOperations < pendingAdmissions)
                throw new IllegalArgumentException("Ownership counts must be nonnegative and include pending admissions");
            if (phase == Phase.TERMINATED && outstandingOperations != 0)
                throw new IllegalArgumentException("Successful termination cannot retain outstanding operations");
            this.failure = failure;
            this.pendingAdmissions = pendingAdmissions;
            this.outstandingOperations = outstandingOperations;
        }
        public Phase phase() { return phase; }
        public Optional<Throwable> failure() { return Optional.ofNullable(failure); }
        public int pendingAdmissions() { return pendingAdmissions; }
        public int outstandingOperations() { return outstandingOperations; }
    }

    Snapshot snapshot();
    /** Waits for successful termination; cleanup failure is reported as FeedException. */
    void await() throws InterruptedException;
    /** A timeout ends only this wait. */
    void await(Duration timeout) throws InterruptedException, TimeoutException;
}
