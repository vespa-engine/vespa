// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reindexing status for a single Vespa application.
 *
 * @author jonmv
 */
public class ApplicationReindexing {

    private final boolean enabled;
    private final Map<String, Cluster> clusters;

    public ApplicationReindexing(boolean enabled, Map<String, Cluster> clusters) {
        this.enabled = enabled;
        this.clusters = Map.copyOf(clusters);
    }

    public boolean enabled() {
        return enabled;
    }

    public Map<String, Cluster> clusters() {
        return clusters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationReindexing that = (ApplicationReindexing) o;
        return enabled == that.enabled &&
               clusters.equals(that.clusters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, clusters);
    }

    @Override
    public String toString() {
        return "ApplicationReindexing{" +
               "enabled=" + enabled +
               ", clusters=" + clusters +
               '}';
    }


    public static class Cluster {

        private final Map<String, Long> pending;
        private final Map<String, Status> ready;

        public Cluster(Map<String, Long> pending, Map<String, Status> ready) {
            this.pending = Map.copyOf(pending);
            this.ready = Map.copyOf(ready);
        }

        public Map<String, Long> pending() {
            return pending;
        }

        public Map<String, Status> ready() {
            return ready;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Cluster cluster = (Cluster) o;
            return pending.equals(cluster.pending) &&
                   ready.equals(cluster.ready);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pending, ready);
        }

        @Override
        public String toString() {
            return "Cluster{" +
                   ", pending=" + pending +
                   ", ready=" + ready +
                   '}';
        }

    }


    public static class Status {

        private final Instant readyAt;
        private final Instant startedAt;
        private final Instant endedAt;
        private final State state;
        private final String message;
        private final Double progress;

        public Status(Instant readyAt, Instant startedAt, Instant endedAt, State state, String message, Double progress) {
            this.readyAt = readyAt;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.state = state;
            this.message = message;
            this.progress = progress;
        }

        public Status(Instant readyAt) {
            this(readyAt, null, null, null, null, null);
        }

        public Optional<Instant> readyAt() { return Optional.ofNullable(readyAt); }
        public Optional<Instant> startedAt() { return Optional.ofNullable(startedAt); }
        public Optional<Instant> endedAt() { return Optional.ofNullable(endedAt); }
        public Optional<State> state() { return Optional.ofNullable(state); }
        public Optional<String> message() { return Optional.ofNullable(message); }
        public Optional<Double> progress() { return Optional.ofNullable(progress); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Status status = (Status) o;
            return Objects.equals(readyAt, status.readyAt) &&
                   Objects.equals(startedAt, status.startedAt) &&
                   Objects.equals(endedAt, status.endedAt) &&
                   state == status.state &&
                   Objects.equals(message, status.message) &&
                   Objects.equals(progress, status.progress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(readyAt, startedAt, endedAt, state, message, progress);
        }

        @Override
        public String toString() {
            return "Status{" +
                   "readyAt=" + readyAt +
                   ", startedAt=" + startedAt +
                   ", endedAt=" + endedAt +
                   ", state=" + state +
                   ", message='" + message + '\'' +
                   ", progress='" + progress + '\'' +
                   '}';
        }

    }


    public enum State {

        PENDING, RUNNING, FAILED, SUCCESSFUL;

    }

}
