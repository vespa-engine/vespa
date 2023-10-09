// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import java.util.ListIterator;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class InvalidatingIterator implements ListIterator<SpanNode> {
    private SpanList owner;
    private ListIterator<SpanNode> base;
    private SpanNode returnedFromNext = null;

    InvalidatingIterator(SpanList owner, ListIterator<SpanNode> base) {
        this.owner = owner;
        this.base = base;
    }

    @Override
    public boolean hasNext() {
        returnedFromNext = null;
        return base.hasNext();
    }

    @Override
    public SpanNode next() {
        SpanNode retval = null;
        try {
            retval = base.next();
        } finally {
            returnedFromNext = retval;
        }
        return returnedFromNext;
    }

    @Override
    public boolean hasPrevious() {
        returnedFromNext = null;
        return base.hasPrevious();
    }

    @Override
    public SpanNode previous() {
        returnedFromNext = null;
        return base.previous();
    }

    @Override
    public int nextIndex() {
        returnedFromNext = null;
        return base.nextIndex();
    }

    @Override
    public int previousIndex() {
        returnedFromNext = null;
        return base.previousIndex();
    }

    @Override
    public void remove() {
        if (returnedFromNext != null) {
            returnedFromNext.setInvalid();
            returnedFromNext.setParent(null);
            owner.resetCachedFromAndTo();
        }
        returnedFromNext = null;
        base.remove();
    }

    @Override
    public void set(SpanNode spanNode) {
        if (returnedFromNext != null) {
            returnedFromNext.setInvalid();
            returnedFromNext.setParent(null);
        }
        owner.resetCachedFromAndTo();
        returnedFromNext = null;
        base.set(spanNode);
    }

    @Override
    public void add(SpanNode spanNode) {
        returnedFromNext = null;
        owner.resetCachedFromAndTo();
        base.add(spanNode);
    }
}
