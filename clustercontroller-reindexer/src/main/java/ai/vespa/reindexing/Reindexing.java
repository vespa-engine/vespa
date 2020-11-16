// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import com.yahoo.document.DocumentType;
import com.yahoo.documentapi.ProgressToken;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Reindexing status per document type.
 *
 * @author jonmv
 */
public class Reindexing {

    private static Reindexing empty = new Reindexing(Map.of());

    private final Map<DocumentType, Status> status;

    Reindexing(Map<DocumentType, Status> status) {
        this.status = Map.copyOf(status);
    }

    public static Reindexing empty() {
        return empty;
    }

    public Reindexing with(DocumentType documentType, Status updated) {
        return new Reindexing(Stream.concat(Stream.of(documentType),
                                            status.keySet().stream())
                                    .distinct()
                                    .collect(toUnmodifiableMap(type -> type,
                                                               type -> documentType.equals(type) ? updated : status.get(type))));
    }

    /** Reindexing status per document type, for types where this is known. */
    public Map<DocumentType, Status> status() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reindexing that = (Reindexing) o;
        return status.equals(that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status);
    }

    @Override
    public String toString() {
        return "Reindexing status " + status;
    }

    /**
     * Reindexing status for a single document type, in an application. Immutable.
     *
     * Reindexing starts at a given instant, and is progressed by visitors.
     */
    public static class Status {

        private final Instant startedAt;
        private final Instant endedAt;
        private final ProgressToken progress;
        private final State state;
        private final String message;

        Status(Instant startedAt, Instant endedAt, ProgressToken progress, State state, String message) {
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.progress = progress;
            this.state = state;
            this.message = message;
        }

        /** Returns a new, empty status, with no progress or result, in state READY. */
        public static Status ready(Instant now) {
            return new Status(requireNonNull(now), null, null, State.READY, null);
        }

        /** Returns a copy of this, in state RUNNING. */
        public Status running() {
            if (state != State.READY)
                throw new IllegalStateException("Current state must be READY when changing to RUNNING");
            return new Status(startedAt, null, progress, State.RUNNING, null);
        }

        /** Returns a copy of this with the given progress. */
        public Status progressed(ProgressToken progress) {
            if (state != State.RUNNING)
                throw new IllegalStateException("Current state must be RUNNING when updating progress");
            return new Status(startedAt, null, requireNonNull(progress), state, null);
        }

        /** Returns a copy of this in state HALTED. */
        public Status halted() {
            if (state != State.RUNNING)
                throw new IllegalStateException("Current state must be RUNNING when changing to READY");
            return new Status(startedAt, null, progress, State.READY, null);
        }

        /** Returns a copy of this with the given end instant, in state SUCCESSFUL. */
        public Status successful(Instant now) {
            if (state != State.RUNNING)
                throw new IllegalStateException("Current state must be RUNNING when changing to SUCCESSFUL");
            return new Status(startedAt, requireNonNull(now), null, State.SUCCESSFUL, null);
        }

        /** Returns a copy of this with the given end instant and failure message, in state FAILED. */
        public Status failed(Instant now, String message) {
            if (state != State.RUNNING)
                throw new IllegalStateException("Current state must be RUNNING when changing to FAILED");
            return new Status(startedAt, requireNonNull(now), progress, State.FAILED, requireNonNull(message));
        }

        public Instant startedAt() {
            return startedAt;
        }

        public Optional<Instant> endedAt() {
            return Optional.ofNullable(endedAt);
        }

        public Optional<ProgressToken> progress() {
            return Optional.ofNullable(progress);
        }

        public State state() {
            return state;
        }

        public Optional<String> message() {
            return Optional.ofNullable(message);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Status status = (Status) o;
            return startedAt.equals(status.startedAt) &&
                   Objects.equals(endedAt, status.endedAt) &&
                   Objects.equals(progress().map(ProgressToken::serializeToString),
                                  status.progress().map(ProgressToken::serializeToString)) &&
                   state == status.state &&
                   Objects.equals(message, status.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(startedAt, endedAt, progress().map(ProgressToken::serializeToString), state, message);
        }

        @Override
        public String toString() {
            return state + (message != null ? " (" + message + ")" : "") +
                   ", started at " + startedAt +
                   (endedAt != null ? ", ended at " + endedAt : "") +
                   (progress != null ? ", with progress " + progress : "");
        }

    }


    public enum State {

        /** Visit ready to be started. */
        READY,

        /** Visit currently running. */
        RUNNING,

        /** Visit completed successfully. */
        SUCCESSFUL,

        /** Visit failed fatally. */
        FAILED

    }

}
