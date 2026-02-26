package com.yahoo.vespa.config.server.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

/**
 *
 * Restarts for a set of hostnames. A pending restart is created for a specific config generation
 * and a set of hostnames.
 *
 * @author Jon Marius Venstad
 *
 *
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

    /**
     * Returns a new {@link PendingRestarts} without the given {@code hostnames} for generations up to and including {@code generation}.
     */
    public PendingRestarts withoutPreviousGenerations(long generation, Set<String> hostnames) {
        Map<Long, Set<String>> newRestarts = new LinkedHashMap<>();
        generationsForRestarts.forEach((restartGen, restartHosts) -> {
            Set<String> remainingHosts = new LinkedHashSet<>(restartHosts);

            if (restartGen <= generation) {
                remainingHosts.removeAll(hostnames);
            }

            if (!remainingHosts.isEmpty()) {
                newRestarts.put(restartGen, remainingHosts);
            }
        });
        return new PendingRestarts(newRestarts);
    }

    public Set<String> hostnames() {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        generationsForRestarts.forEach((g, hostnames) -> all.addAll(hostnames));
        return all;
    }
    
    // Restarts are ready to be done for all hostnames that report a config generation (through
    // convergence checker) that is equal to or larger than the config generation that the restart
    // should happen on
    public Set<String> restartsReadyAt(long generation) {
        LinkedHashSet<String> ready = new LinkedHashSet<>();
        generationsForRestarts.forEach((g, hostnames) -> { if (generation >= g) ready.addAll(hostnames); });
        return ready;
    }
}
