// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.api.Reindexing;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Pending and ready reindexing per document type. Each document type can have either a pending or a ready reindexing.
 * This is immutable.
 *
 * @author jonmv
 */
public class ReindexingStatus implements Reindexing {

    private static final ReindexingStatus empty = new ReindexingStatus(Map.of(), Map.of());

    private final Map<String, Long> pending;
    private final Map<String, Status> ready;

    ReindexingStatus(Map<String, Long> pending, Map<String, Status> ready) {
        this.pending = Map.copyOf(pending);
        this.ready = ready;
    }

    /** No reindexing pending or ready. */
    public static ReindexingStatus empty() {
        return empty;
    }

    /** Returns a copy of this with a pending reindexing at the given generation, for the given document type. */
    public ReindexingStatus withPending(String documentType, long requiredGeneration) {
        return new ReindexingStatus(with(documentType, requirePositive(requiredGeneration), pending),
                                    without(documentType, ready));
    }

    /** Returns a copy of this with reindexing for the given document type set ready at the given instant. */
    public ReindexingStatus withReady(String documentType, Instant readyAt) {
        return new ReindexingStatus(without(documentType, pending),
                                    with(documentType, new Status(readyAt), ready));
    }

    /** The config generation at which the application must have converged for the latest reindexing to begin, per document type.  */
    public Map<String, Long> pending() {
        return pending;
    }

    @Override
    public Map<String, ? extends Reindexing.Status> status() {
        return ready;
    }

    private static long requirePositive(long generation) {
        if (generation <= 0)
            throw new IllegalArgumentException("Generation must be positive, but was " + generation);

        return generation;
    }

    private static <T> Map<String, T> without(String removed, Map<String, T> map) {
        return map.keySet().stream()
                  .filter(key -> ! removed.equals(key))
                  .collect(toUnmodifiableMap(key -> key,
                                             key -> map.get(key)));
    }

    private static <T> Map<String, T> with(String added, T value, Map<String, T> map) {
        return Stream.concat(Stream.of(added), map.keySet().stream()).distinct()
                     .collect(toUnmodifiableMap(key -> key,
                                                key -> added.equals(key) ? value
                                                                         : map.get(key)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReindexingStatus that = (ReindexingStatus) o;
        return pending.equals(that.pending) &&
               ready.equals(that.ready);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pending, ready);
    }

    @Override
    public String toString() {
        return "ReindexingStatus{" +
               "pending=" + pending +
               ", ready=" + ready +
               '}';
    }

    static class Status implements Reindexing.Status {

        private final Instant ready;

        Status(Instant ready) {
            this.ready = Objects.requireNonNull(ready);
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

}
