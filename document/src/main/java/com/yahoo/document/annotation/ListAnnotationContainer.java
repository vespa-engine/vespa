// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class ListAnnotationContainer extends IteratingAnnotationContainer {
    private final List<Annotation> annotations = new LinkedList<Annotation>();

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
        if (!(o instanceof ListAnnotationContainer)) return false;
        ListAnnotationContainer that = (ListAnnotationContainer) o;
        if (!annotations.equals(that.annotations)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return annotations.hashCode();
    }

    private class AnnotationIterator implements Iterator<Annotation> {
        private IdentityHashMap<SpanNode, SpanNode> nodes;
        private PeekableListIterator<Annotation> base;
        private boolean nextCalled = false;

        AnnotationIterator(ListIterator<Annotation> baseIt, IdentityHashMap<SpanNode, SpanNode> nodes) {
            this.base = new PeekableListIterator<Annotation>(baseIt);
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
