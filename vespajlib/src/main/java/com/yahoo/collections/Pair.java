// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

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
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Pair)) return false;

        @SuppressWarnings("rawtypes")
        final Pair other = (Pair) o;
        return equals(this.first, other.first)
                && equals(this.second, other.second);
    }

    private static boolean equals(final Object a, final Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    @Override
    public String toString() {
        return "(" + first + "," + second + ")";
    }

}
