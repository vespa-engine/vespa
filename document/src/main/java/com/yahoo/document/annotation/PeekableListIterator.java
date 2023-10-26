// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import java.util.ListIterator;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class PeekableListIterator<E> implements ListIterator<E> {
    private E next;
    private ListIterator<E> base;
    boolean traversed = false;
    private int position = -1;

    PeekableListIterator(ListIterator<E> base) {
        this.base = base;
        this.traversed = false;
    }

    @Override
    public boolean hasNext() {
        return next != null || base.hasNext();
    }

    @Override
    public E next() {
        if (next == null) {
            E n = base.next();
            position++;
            return n;
        }
        E retval = next;
        next = null;
        position++;
        return retval;
    }

    @Override
    public boolean hasPrevious() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E previous() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int nextIndex() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int previousIndex() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove() {
        if (position < 0) {
            //we have not next'ed the iterator, cannot do this:
            throw new IllegalStateException("Cannot remove() before next()");
        }
        if (next != null) {
            //we have already gone one step ahead. must back up two positions and then remove:
            base.previous();
            base.previous();
            base.remove();
        } else {
            base.remove();
        }
        next = null;
    }

    @Override
    public void set(E e) {
        if (position < 0) {
            //we have not next'ed the iterator, cannot do this:
            throw new IllegalStateException("Cannot set() before next()");
        }
        if (next != null) {
            //we have already gone one step ahead. must back up two positions and then remove:
            base.previous();
            base.previous();
        }
        base.set(e);
        next = null;

    }

    @Override
    public void add(E e) {
        if (next != null) {
            //we have already gone one step ahead. must back up one position and then add:
            base.previous();
        }
        base.add(e);
        next = null;
    }

    public E peek() {
        if (next == null && base.hasNext()) {
            next = base.next();
        }
        return next;
    }
}
