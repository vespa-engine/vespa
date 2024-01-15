package com.yahoo.vespa.config.server.application;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

/**
 * @author jonmv
 */
public class PendingRestarts {

    private static final PendingRestarts empty = new PendingRestarts(Map.of());

    private final Map<Long, Set<String>> generationsForRestarts;

    public static PendingRestarts empty() {
        return empty;
    }

    PendingRestarts(Map<Long, ? extends Collection<String>> generationsForRestarts) {
        Map<Long, Set<String>> builder = new LinkedHashMap<>();
        generationsForRestarts.forEach((generation, hostnames) -> builder.put(generation, unmodifiableSet(new LinkedHashSet<>(hostnames))));
        this.generationsForRestarts = unmodifiableMap(builder);
    }

    public Map<Long, Set<String>> generationsForRestarts() { return generationsForRestarts; }

    public boolean isEmpty() { return generationsForRestarts.isEmpty(); }

    public PendingRestarts withRestarts(long atGeneration, Collection<String> hostnames) {
        Map<Long, Set<String>> newRestarts = new LinkedHashMap<>(generationsForRestarts);
        newRestarts.put(atGeneration, new LinkedHashSet<>(newRestarts.getOrDefault(atGeneration, Set.of())) {{ addAll(hostnames); }});
        return new PendingRestarts(newRestarts);
    }

    public PendingRestarts withoutPreviousGenerations(long generation) {
        Map<Long, Set<String>> newRestarts = new LinkedHashMap<>(generationsForRestarts);
        newRestarts.keySet().removeIf(g -> g <= generation);
        return new PendingRestarts(newRestarts);
    }

    public Set<String> hostnames() {
        return restartsReadyAt(Long.MAX_VALUE);
    }

    public Set<String> restartsReadyAt(long generation) {
        LinkedHashSet<String> ready = new LinkedHashSet<>();
        generationsForRestarts.forEach((g, hosts) -> { if (g <= generation) ready.addAll(hosts); });
        return ready;
    }

}
