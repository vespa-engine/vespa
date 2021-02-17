// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies;

import java.util.*;

import com.google.common.collect.ImmutableSet;

/**
 * Constraints for ordering ChainedComponents in chains. Immutable.
 *
 * @author Tony Vaagenes
 */
public class Dependencies {

    private final Set<String> provides;
    private final Set<String> before;
    private final Set<String> after;

    /**
     * Create from collections of strings, typically from config.
     */
    public Dependencies(Collection<String> provides, Collection<String> before, Collection<String> after) {
        this.provides = immutableSet(provides);
        this.before = immutableSet(before);
        this.after = immutableSet(after);
    }

    public static Dependencies emptyDependencies() {
        return new Dependencies(null, null, null);
    }

    public Dependencies union(Dependencies dependencies) {
        return new Dependencies(
                union(provides, dependencies.provides),
                union(before, dependencies.before),
                union(after, dependencies.after));
    }

    private Set<String> immutableSet(Collection<String> set) {
        if (set == null) return ImmutableSet.of();
        return ImmutableSet.copyOf(set);
    }

    private Set<String> union(Set<String> s1, Set<String> s2) {
        Set<String> result = new LinkedHashSet<>(s1);
        result.addAll(s2);
        return result;
    }

    @Override
    public String toString() {
        return "Dependencies{" +
                "provides=" + provides +
                ", before=" + before +
                ", after=" + after +
                '}';
    }

    public Set<String> provides() {
        return provides;
    }

    public Set<String> before() {
        return before;
    }

    public Set<String> after() {
        return after;
    }

}
