// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * ListIterator implementation which performs a depth-first traversal of SpanNodes.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class RecursiveNodeIterator implements ListIterator<SpanNode> {
    protected Deque<PeekableListIterator<SpanNode>> stack = new ArrayDeque<>();
    protected ListIterator<SpanNode> iteratorFromLastCallToNext = null;

    RecursiveNodeIterator(ListIterator<SpanNode> it) {
        stack.push(new PeekableListIterator<>(it));
    }

    protected RecursiveNodeIterator() {
    }

    @Override
    public boolean hasNext() {
        if (stack.isEmpty()) {
            return false;
        }
        PeekableListIterator<SpanNode> iterator = stack.peek();
        if (!iterator.hasNext()) {
            stack.pop();
            return hasNext();
        }


        SpanNode node = iterator.peek();

        if (!iterator.traversed) {
            //we set the traversed flag on our way down
            iterator.traversed = true;
            stack.push(new PeekableListIterator<>(node.childIterator()));
            return hasNext();
        }

        return true;
    }

    @Override
    public SpanNode next() {
        if (stack.isEmpty() || !hasNext()) {
            iteratorFromLastCallToNext = null;
            throw new NoSuchElementException("No next element available.");
        }
        stack.peek().traversed = false;
        iteratorFromLastCallToNext = stack.peek();
        return stack.peek().next();
    }

    @Override
    public boolean hasPrevious() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SpanNode previous() {
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
        if (stack.isEmpty()) {
            throw new IllegalStateException();
        }
        if (iteratorFromLastCallToNext != null) {
            iteratorFromLastCallToNext.remove();
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public void set(SpanNode spanNode) {
        if (stack.isEmpty()) {
            throw new IllegalStateException();
        }
        if (iteratorFromLastCallToNext != null) {
            iteratorFromLastCallToNext.set(spanNode);
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public void add(SpanNode spanNode) {
        throw new UnsupportedOperationException();
    }
}
