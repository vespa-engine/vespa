// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import org.apache.commons.collections.map.MultiValueMap;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
// TODO: Should this be removed?
public class AnnotationType2AnnotationContainer extends IteratingAnnotationContainer {
    private final MultiValueMap annotationType2Annotation = MultiValueMap.decorate(new IdentityHashMap());

    @Override
    void annotateAll(Collection<Annotation> annotations) {
        for (Annotation a : annotations) {
            annotate(a);
        }
    }

    @Override
    void annotate(Annotation annotation) {
        annotationType2Annotation.put(annotation.getType(), annotation);
    }

    @Override
    @SuppressWarnings("unchecked")
    Collection<Annotation> annotations() {
        return annotationType2Annotation.values();
    }

    @Override
    Iterator<Annotation> iterator(IdentityHashMap<SpanNode, SpanNode> nodes) {
        return new NonRecursiveIterator(nodes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnnotationType2AnnotationContainer)) return false;
        AnnotationType2AnnotationContainer that = (AnnotationType2AnnotationContainer) o;
        if (!annotationType2Annotation.equals(that.annotationType2Annotation)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return annotationType2Annotation.hashCode();
    }

    private class NonRecursiveIterator implements Iterator<Annotation> {
        private final IdentityHashMap<SpanNode, SpanNode> nodes;
        private final Iterator<Annotation> annotationIt;
        private Annotation next = null;
        private boolean nextCalled;

        @SuppressWarnings("unchecked")
        public NonRecursiveIterator(IdentityHashMap<SpanNode, SpanNode> nodes) {
            this.nodes = nodes;
            this.annotationIt = annotationType2Annotation.values().iterator();
        }

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            while (annotationIt.hasNext()) {
                Annotation tmp = annotationIt.next();
                if (nodes.containsKey(tmp.getSpanNodeFast())) {
                    next = tmp;
                    return true;
                }
            }
            next = null;
            return false;
        }

        @Override
        public Annotation next() {
            if (hasNext()) {
                Annotation tmp = next;
                next = null;
                nextCalled = true;
                return tmp;
            }
            //there is no 'next'
            throw new NoSuchElementException("No next element found.");
        }

        @Override
        public void remove() {
            //only allowed to call remove immediately after next()
            if (!nextCalled) {
                //we have not next'ed the iterator, cannot do this:
                throw new IllegalStateException("Cannot remove() before next()");
            }
            annotationIt.remove();
            nextCalled = false;
        }
    }
}
