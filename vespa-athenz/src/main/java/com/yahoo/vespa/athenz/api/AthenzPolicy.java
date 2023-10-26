// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.List;
import java.util.Objects;

/**
 * @author bjorncs
 */
public class AthenzPolicy {
    private final String name;
    private final List<AthenzAssertion> assertions;

    public AthenzPolicy(String name, List<AthenzAssertion> assertions) {
        this.assertions = List.copyOf(assertions);
        this.name = name;
    }

    public String name() { return name; }
    public List<AthenzAssertion> assertions() { return assertions; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthenzPolicy that = (AthenzPolicy) o;
        return Objects.equals(name, that.name) && Objects.equals(assertions, that.assertions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, assertions);
    }

    @Override
    public String toString() {
        return "AthenzPolicy{" +
                "name='" + name + '\'' +
                ", assertions=" + assertions +
                '}';
    }
}
