// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reindexing status for each document type in a content cluster.
 *
 * @author jonmv
 * @author bjorncs
 */
public class ClusterReindexing {

    private static final ClusterReindexing empty = new ClusterReindexing(Map.of());

    private final Map<String, Status> documentTypeStatus;

    public ClusterReindexing(Map<String, Status> documentTypeStatus) {
        this.documentTypeStatus = Map.copyOf(documentTypeStatus);
    }

    public static ClusterReindexing empty() { return empty; }

    public Map<String, Status> documentTypeStatus() { return documentTypeStatus; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterReindexing that = (ClusterReindexing) o;
        return documentTypeStatus.equals(that.documentTypeStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentTypeStatus);
    }

    @Override
    public String toString() {
        return "ClusterReindexing{" +
               "documentTypeStatus=" + documentTypeStatus +
               '}';
    }


    public static class Status {

        private final Instant startedAt;
        private final Instant endedAt;
        private final State state;
        private final String message;
        private final Double progress;

        public Status(Instant startedAt, Instant endedAt, State state, String message, Double progress) {
            this.startedAt = Objects.requireNonNull(startedAt);
            this.endedAt = endedAt;
            this.state = state;
            this.message = message;
            this.progress = progress;
        }

        public Instant startedAt() { return startedAt; }
        public Optional<Instant> endedAt() { return Optional.ofNullable(endedAt); }
        public Optional<State> state() { return Optional.ofNullable(state); }
        public Optional<String> message() { return Optional.ofNullable(message); }
        public Optional<Double> progress() { return Optional.ofNullable(progress); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Status status = (Status) o;
            return startedAt.equals(status.startedAt) &&
                   Objects.equals(endedAt, status.endedAt) &&
                   state == status.state &&
                   Objects.equals(message, status.message) &&
                   Objects.equals(progress, status.progress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(startedAt, endedAt, state, message, progress);
        }

        @Override
        public String toString() {
            return "Status{" +
                   "startedAt=" + startedAt +
                   ", endedAt=" + endedAt +
                   ", state=" + state +
                   ", message='" + message + '\'' +
                   ", progress='" + progress + '\'' +
                   '}';
        }

    }


    public enum State {
        PENDING("pending"), RUNNING("running"), FAILED("failed"), SUCCESSFUL("successful");

        private final String stringValue;

        State(String stringValue) { this.stringValue = stringValue; }

        public static State fromString(String value) {
            return Arrays.stream(values())
                    .filter(v -> v.stringValue.equals(value))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown value: " + value));
        }

        public String asString() { return stringValue; }

    }

}
