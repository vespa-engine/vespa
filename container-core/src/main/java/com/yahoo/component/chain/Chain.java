// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.ordering.ChainBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An immutable ordered list of components
 *
 * @author Tony Vaagenes
 */
public class Chain<COMPONENT extends ChainedComponent> {

    final private List<COMPONENT> componentList;
    private final ComponentId id;

    /** Create a chain directly. This will NOT order the chain by the ordering constraints. */
    public Chain(String id, List<COMPONENT> componentList) {
        this(new ComponentId(id), componentList);
    }

    /** Create a chain directly. This will NOT order the chain by the ordering constraints. */
    public Chain(ComponentId id, List<COMPONENT> componentList) {
        this.id = id;
        this.componentList = ImmutableList.copyOf(componentList);
    }

    /** Create a chain directly. This will NOT order the chain by the ordering constraints. */
    public Chain(List<COMPONENT> componentList) {
        this(new ComponentId("anonymous chain"), componentList);
    }

    /** Create a chain directly. This will NOT order the chain by the ordering constraints. */
    @SafeVarargs
    public Chain(COMPONENT... components) {
        this("anonymous chain", components);
    }

    /** Create a chain directly. This will NOT order the chain by the ordering constraints. */
    @SafeVarargs
    public Chain(String id, COMPONENT... components) {
        this(new ComponentId(id), components);
    }

    /** Create a chain directly. This will NOT order the chain by the ordering constraints. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public Chain(ComponentId id, COMPONENT... components) {
        this(id, Arrays.<COMPONENT>asList(components));
    }

    /** Create a chain by using a builder. This will order the chain by the ordering constraints. */
    public Chain(ComponentId id, Collection<COMPONENT> components, Collection<Phase> phases) {
        this(id, buildChain(
                emptyListIfNull(components),
                emptyListIfNull(phases)).components());

    }

    public ComponentId getId() {
        return id;
    }

    private static <T> Collection<T> emptyListIfNull(Collection<T> collection) {
        return collection == null ? Collections.<T>emptyList() : collection;
    }

    private static <T extends ChainedComponent> Chain<T> buildChain(Collection<T> components, Collection<Phase> phases) {
        ChainBuilder<T> builder = new ChainBuilder<>(new ComponentId("temp"));
        for (Phase phase : phases) {
            builder.addPhase(phase);
        }

        for (T component : components) {
            builder.addComponent(component);
        }

        return builder.orderNodes();
    }

    public List<COMPONENT> components() {
        return componentList;
    }

    public
    @Override
    String toString() {
        StringBuilder b = new StringBuilder("chain '");
        b.append(getId().stringValue());
        b.append("' [");
        appendComponent(0, b);
        appendComponent(1, b);
        if (components().size() > 3)
            b.append("... -> ");
        if (components().size() > 2)
            appendComponent(components().size() - 1, b);
        b.append("]");
        return b.toString();
    }

    private void appendComponent(int i, StringBuilder b) {
        if (i >= components().size()) return;
        b.append(components().get(i).getId().stringValue());
        if (i < components().size() - 1)
            b.append(" -> ");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Chain<?> chain = (Chain<?>) o;

        if (!componentList.equals(chain.componentList)) return false;
        if (!id.equals(chain.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = componentList.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }
}
