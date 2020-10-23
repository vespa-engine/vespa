// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.Objects;

/**
 * An immutable pair of objects. This implements equals and hashCode by delegating to the
 * pair objects.
 *
 * @author bratseth
 */
public class Pair<F, S> {

    /** The first member for the pair. May be null. */
    private final F first;
    /** The second member for the pair. May be null. */
    private final S second;

    /** Creates a pair. Each member may be set to null. */
    public Pair(final F first, final S second) {
        this.first = first;
        this.second = second;
    }

    /** Returns the first member. This may be null. */
    public F getFirst() { return first; }

    /** Returns the second member. This may be null. */
    public S getSecond() { return second; }

    @Override
    public int hashCode() {
        return ( first != null ? first.hashCode() : 0 ) +
               ( second != null ? 17*second.hashCode() : 0) ;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Pair)) return false;

        @SuppressWarnings("rawtypes")
        Pair other = (Pair) o;
        return Objects.equals(this.first, other.first) && Objects.equals(this.second, other.second);
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

}
