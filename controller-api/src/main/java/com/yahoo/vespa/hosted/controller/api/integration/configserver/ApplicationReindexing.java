// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Reindexing status for a single Vespa application.
 *
 * @author jonmv
 */
public class ApplicationReindexing {

    private final boolean enabled;
    private final Status common;
    private final Map<String, Cluster> clusters;

    public ApplicationReindexing(boolean enabled, Status common, Map<String, Cluster> clusters) {
        this.enabled = enabled;
        this.common = requireNonNull(common);
        this.clusters = Map.copyOf(clusters);
    }

    public boolean enabled() {
        return enabled;
    }

    public Status common() {
        return common;
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
               common.equals(that.common) &&
               clusters.equals(that.clusters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, common, clusters);
    }

    @Override
    public String toString() {
        return "ApplicationReindexing{" +
               "enabled=" + enabled +
               ", common=" + common +
               ", clusters=" + clusters +
               '}';
    }


    public static class Cluster {

        private final Status common;
        private final Map<String, Long> pending;
        private final Map<String, Status> ready;

        public Cluster(Status common, Map<String, Long> pending, Map<String, Status> ready) {
            this.common = requireNonNull(common);
            this.pending = Map.copyOf(pending);
            this.ready = Map.copyOf(ready);
        }

        public Status common() {
            return common;
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
            return common.equals(cluster.common) &&
                   pending.equals(cluster.pending) &&
                   ready.equals(cluster.ready);
        }

        @Override
        public int hashCode() {
            return Objects.hash(common, pending, ready);
        }

        @Override
        public String toString() {
            return "Cluster{" +
                   "common=" + common +
                   ", pending=" + pending +
                   ", ready=" + ready +
                   '}';
        }

    }


    public static class Status {

        private final Instant readyAt;

        public Status(Instant readyAt) {
            this.readyAt = requireNonNull(readyAt);
        }

        public Instant readyAt() { return readyAt; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Status status = (Status) o;
            return readyAt.equals(status.readyAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(readyAt);
        }

        @Override
        public String toString() {
            return "ready at " + readyAt;
        }

    }

}
