// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.api.Reindexing;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.search.DocumentDatabase;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * Pending reindexing: convergence to the stored config generation allows reindexing to start.
 * Ready reindexing: reindexing may start after this timestamp.
 * This is immutable.
 *
 * @author jonmv
 */
public class ApplicationReindexing implements Reindexing {

    private final boolean enabled;
    private final Map<String, Cluster> clusters;

    ApplicationReindexing(boolean enabled, Map<String, Cluster> clusters) {
        this.enabled = enabled;
        this.clusters = Map.copyOf(clusters);
    }

    /** Reindexing for the whole application ready now. */
    public static ApplicationReindexing empty() {
        return new ApplicationReindexing(true, Map.of());
    }

    /** Returns a copy of this with reindexing for the given document type in the given cluster ready at the given instant. */
    public ApplicationReindexing withReady(String cluster, String documentType, Instant readyAt) {
        Cluster current = clusters.getOrDefault(cluster, Cluster.empty());
        Cluster modified = new Cluster(current.pending,
                                       with(documentType, new Status(readyAt), current.ready));
        return new ApplicationReindexing(enabled, with(cluster, modified, clusters));
    }

    /** Returns a copy of this with a pending reindexing at the given generation, for the given document type. */
    public ApplicationReindexing withPending(String cluster, String documentType, long requiredGeneration) {
        Cluster current = clusters.getOrDefault(cluster, Cluster.empty());
        Cluster modified = new Cluster(with(documentType, requirePositive(requiredGeneration), current.pending),
                                       current.ready);
        return new ApplicationReindexing(enabled, with(cluster, modified, clusters));
    }

    /** Returns a copy of this with no pending reindexing for the given document type. */
    public ApplicationReindexing withoutPending(String cluster, String documentType) {
        Cluster current = clusters.getOrDefault(cluster, Cluster.empty());
        if (current == null)
            return this;

        Cluster modified = new Cluster(without(documentType, current.pending),
                                       current.ready);
        return new ApplicationReindexing(enabled, with(cluster, modified, clusters));
    }

    /** Returns a copy of this without the given cluster. */
    public ApplicationReindexing without(String cluster) {
        return new ApplicationReindexing(enabled, without(cluster, clusters));
    }

    /** Returns a copy of this without the given document type in the given cluster. */
    public ApplicationReindexing without(String cluster, String documentType) {
        Cluster current = clusters.get(cluster);
        if (current == null)
            return this;

        Cluster modified = new Cluster(current.pending,
                                       without(documentType, current.ready));
        return new ApplicationReindexing(enabled, with(cluster, modified, clusters));
    }

    /** Returns a copy of this with the enabled-state set to the given value. */
    public ApplicationReindexing enabled(boolean enabled) {
        return new ApplicationReindexing(enabled, clusters);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    /** The reindexing status of each of the clusters of this application. */
    public Map<String, Cluster> clusters() { return clusters; }

    @Override
    public Optional<Reindexing.Status> status(String clusterName, String documentType) {
        return Optional.ofNullable(clusters.get(clusterName)).map(cluster -> cluster.ready().get(documentType));
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

    /** Reindexing status for a single content cluster in an application. */
    public static class Cluster {

        private static Cluster empty() { return new Cluster(Map.of(), Map.of()); }

        private final Map<String, Long> pending;
        private final Map<String, Status> ready;

        Cluster(Map<String, Long> pending, Map<String, Status> ready) {
            this.pending = Map.copyOf(pending);
            this.ready = Map.copyOf(ready);
        }

        /** The config generation at which the application must have converged for the latest reindexing to begin, per document type.  */
        public Map<String, Long> pending() {
            return pending;
        }

        /** The reindexing status for ready document types in this cluster. */
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
                   "pending=" + pending +
                   ", ready=" + ready +
                   '}';
        }

    }


    /** Reindexing status common to an application, one of its clusters, or a single document type in a cluster. */
    public static class Status implements Reindexing.Status {

        private final Instant ready;

        Status(Instant ready) {
            this.ready = ready.truncatedTo(ChronoUnit.MILLIS);
        }

        @Override
        public Instant ready() { return ready; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Status status = (Status) o;
            return ready.equals(status.ready);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ready);
        }

        @Override
        public String toString() {
            return "ready at " + ready;
        }

    }


    private static long requirePositive(long generation) {
        if (generation <= 0)
            throw new IllegalArgumentException("Generation must be positive, but was " + generation);

        return generation;
    }

    private static <T> Map<String, T> without(String removed, Map<String, T> map) {
        Map<String, T> modified = new HashMap<>(map);
        modified.remove(removed);
        return Map.copyOf(modified);
    }

    private static <T> Map<String, T> with(String added, T value, Map<String, T> map) {
        Map<String, T> modified = new HashMap<>(map);
        modified.put(added, value);
        return Map.copyOf(modified);
    }

}
