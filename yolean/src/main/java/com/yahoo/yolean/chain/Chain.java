// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An immutable and ordered list of components
 *
 * @author Tony Vaagenes
 */
public final class Chain<T> implements Iterable<T> {

    private final String id;
    private final Collection<T> components;

    @SafeVarargs
    public Chain(String id, T... components) {
        this(id, Arrays.asList(components));
    }

    public Chain(String id, List<? extends T> components) {
        requireNonNull(id, "id must be non-null.");
        requireNonNull(components, "components must be non-null");

        this.components = ImmutableList.copyOf(components);
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    @Override
    public Iterator<T> iterator() {
        return components.iterator();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("chain '").append(id).append("'{");
        boolean first = true;
        for (T component : components) {
            if (!first) {
                b.append("->");
            } else {
                first = false;
            }
            b.append(" ").append(component.getClass().getSimpleName()).append(" ");
        }
        b.append("}");
        return b.toString();
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Chain && equals((Chain<?>)other);
    }

    public boolean equals(Chain<?> other) {
        return id.equals(other.id) && componentsIdentical(components, other.components);
    }

    private boolean componentsIdentical(Collection<T> components1, Collection<?> components2) {
        if (components1.size() != components2.size()) {
            return false;
        }
        Iterator<T> iterator1 = components1.iterator();
        Iterator<?> iterator2 = components2.iterator();
        while (iterator1.hasNext()) {
            T c1 = iterator1.next();
            Object c2 = iterator2.next();

            if (c1 != c2) {
                return false;
            }
        }
        return true;
    }

}
