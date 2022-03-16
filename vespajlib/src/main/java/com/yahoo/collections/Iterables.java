// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * @author jonmv
 */
public class Iterables {

    private Iterables() { }

    /** Returns a reverse-order iterable view of the given elements. */
    public static <T> Iterable<T> reversed(List<T> elements) {
        return () -> new Iterator<T>() {
            final ListIterator<T> wrapped = elements.listIterator(elements.size());
            @Override public boolean hasNext() { return wrapped.hasPrevious(); }
            @Override public T next() { return wrapped.previous(); }
            @Override public void remove() { wrapped.remove(); }
        };
    }

}
