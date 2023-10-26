// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import java.util.*;

/**
 * An iterator for the forest of hits in a result.
 *
 * @author havardpe
 */
public class DeepHitIterator implements Iterator<Hit> {

    private final boolean ordered;
    private List<Iterator<Hit>> stack;
    private boolean canRemove = false;
    private Iterator<Hit> it = null;
    private Hit next = null;


    /**
     * Create a deep hit iterator based on the given hit iterator.
     *
     * @param it      The hits iterator to traverse.
     * @param ordered Whether or not the hits should be ordered.
     */
    public DeepHitIterator(Iterator<Hit> it, boolean ordered) {
        this.ordered = ordered;
        this.it = it;
    }

    @Override
    public boolean hasNext() {
        canRemove = false;
        return getNext();
    }

    @Override
    public Hit next() throws NoSuchElementException {
        if (next == null && !getNext()) {
            throw new NoSuchElementException();
        }
        Hit ret = next;
        next = null;
        canRemove = true;
        return ret;
    }

    @Override
    public void remove() throws UnsupportedOperationException, IllegalStateException {
        if (!canRemove) {
            throw new IllegalStateException("Can not remove() an element after calling hasNext().");
        }
        it.remove();
    }

    private boolean getNext() {
        if (next != null) {
            return true;
        }

        if (stack == null) {
            stack = new ArrayList<>();
        }
        while (true) {
            if (it.hasNext()) {
                Hit hit = it.next();
                if (hit instanceof HitGroup) {
                    stack.add(it);
                    if (ordered) {
                        it = ((HitGroup)hit).iterator();
                    } else {
                        it = ((HitGroup)hit).unorderedIterator();
                    }
                } else {
                    next = hit;
                    return true;
                }
            } else if (!stack.isEmpty()) {
                it = stack.remove(stack.size()-1);
            } else {
                return false;
            }
        }
    }
}
