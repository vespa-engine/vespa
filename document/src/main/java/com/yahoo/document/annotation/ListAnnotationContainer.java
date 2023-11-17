// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * @author Einar M R Rosenvinge
 */
public class ListAnnotationContainer extends IteratingAnnotationContainer {

    private final List<Annotation> annotations = new LinkedList<>();

    @Override
    void annotateAll(Collection<Annotation> annotations) {
        this.annotations.addAll(annotations);
    }

    @Override
    void annotate(Annotation a) {
        annotations.add(a);
    }

    @Override
    Collection<Annotation> annotations() {
        return annotations;
    }

    @Override
    Iterator<Annotation> iterator(IdentityHashMap<SpanNode, SpanNode> nodes) {
        return new AnnotationIterator(annotations.listIterator(), nodes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListAnnotationContainer other)) return false;
        if (!annotations.equals(other.annotations)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return annotations.hashCode();
    }

    private class AnnotationIterator implements Iterator<Annotation> {

        private final IdentityHashMap<SpanNode, SpanNode> nodes;
        private final PeekableListIterator<Annotation> base;
        private boolean nextCalled = false;

        AnnotationIterator(ListIterator<Annotation> baseIt, IdentityHashMap<SpanNode, SpanNode> nodes) {
            this.base = new PeekableListIterator(baseIt);
            this.nodes = nodes;
        }

        @Override
        public boolean hasNext() {
            nextCalled = false;
            while (base.hasNext() && !nodes.containsKey(base.peek().getSpanNode())) {
                base.next();
            }
            //now either, base has no next, or next is the correct node
            if (base.hasNext()) {
                return true;
            }
            return false;
        }

        @Override
        public Annotation next() {
            if (hasNext()) {
                nextCalled = true;
                return base.next();
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            if (!nextCalled) {
                throw new IllegalStateException();
            }
            base.remove();
            nextCalled = false;
        }
    }

}
