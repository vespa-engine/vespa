// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain;

import com.yahoo.component.chain.dependencies.Dependencies;

import java.util.Set;
import java.util.TreeSet;

/**
 * Used for many to many constraints on searcher ordering. Immutable.
 *
 * @author Tony Vaagenes
 */
public class Phase {

    public final Dependencies dependencies;

    public Phase(String name, Set<String> before, Set<String> after) {
        dependencies = new Dependencies(provides(name), before, after);
    }

    public Phase(String name, Dependencies dependencies) {
        this(name, dependencies.before(), dependencies.after());
        assert(dependencies.provides().isEmpty());
    }

    private Set<String> provides(String name) {
        Set<String> provides = new TreeSet<>();
        provides.add(name);
        return provides;
    }

    public String getName() {
        return dependencies.provides().iterator().next();
    }

    public Set<String> before() {
        return dependencies.before();
    }

    public Set<String> after() {
        return dependencies.after();
    }

    public Phase union(Phase phase) {
        assert(getName().equals(phase.getName()));

        Dependencies union = dependencies.union(phase.dependencies);
        return new Phase(getName(), union.before(), union.after());
    }

}
