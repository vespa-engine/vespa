// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import org.apache.commons.collections.map.MultiValueMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

/**
 * TODO: Should this be removed?
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
class SpanNode2AnnotationContainer extends AnnotationContainer {
    private final MultiValueMap spanNode2Annotation = MultiValueMap.decorate(new IdentityHashMap());

    @Override
    void annotateAll(Collection<Annotation> annotations) {
        for (Annotation a : annotations) {
            annotate(a);
        }
    }

    @Override
    void annotate(Annotation a) {
        if (a.getSpanNode() == null) {
            spanNode2Annotation.put(DummySpanNode.INSTANCE, a);
        } else {
            spanNode2Annotation.put(a.getSpanNode(), a);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    Collection<Annotation> annotations() {
        return spanNode2Annotation.values();
    }

    @Override
    @SuppressWarnings("unchecked")
    Iterator<Annotation> iterator(SpanNode node) {
        Collection<Annotation> annotationsForNode = spanNode2Annotation.getCollection(node);
        if (annotationsForNode == null) {
            return Collections.<Annotation>emptyList().iterator();
        }
        return annotationsForNode.iterator();    }

    @Override
    @SuppressWarnings("unchecked")
    Iterator<Annotation> iteratorRecursive(SpanNode node) {
        IdentityHashMap<SpanNode, SpanNode> nodes = new IdentityHashMap<SpanNode, SpanNode>();
        nodes.put(node, node);
        {
            Iterator<SpanNode> childrenIt = node.childIteratorRecursive();
            while (childrenIt.hasNext()) {
                SpanNode child = childrenIt.next();
                nodes.put(child, child);
            }
        }
        List<Collection<Annotation>> annotationLists = new ArrayList<Collection<Annotation>>(nodes.size());
        for (SpanNode includedNode : nodes.keySet()) {
            Collection<Annotation> includedAnnotations = spanNode2Annotation.getCollection(includedNode);
            if (includedAnnotations != null) {
                annotationLists.add(includedAnnotations);
            }
        }
        return new AnnotationCollectionIterator(annotationLists);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpanNode2AnnotationContainer)) return false;
        SpanNode2AnnotationContainer that = (SpanNode2AnnotationContainer) o;
        if (!spanNode2Annotation.equals(that.spanNode2Annotation)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return spanNode2Annotation.hashCode();
    }

    private class AnnotationCollectionIterator implements Iterator<Annotation> {
        private final List<Collection<Annotation>> annotationLists;
        private Iterator<Annotation> currentIterator;
        private boolean nextCalled = false;

        AnnotationCollectionIterator(List<Collection<Annotation>> annotationLists) {
            this.annotationLists = annotationLists;
            if (annotationLists.isEmpty()) {
                currentIterator = Collections.<Annotation>emptyList().iterator();
            } else {
                currentIterator = annotationLists.remove(0).iterator();
            }
        }

        @Override
        public boolean hasNext() {
            nextCalled = false;
            if (currentIterator.hasNext()) {
                return true;
            }
            if (annotationLists.isEmpty()) {
                return false;
            }
            currentIterator = annotationLists.remove(0).iterator();
            return hasNext();
        }

        @Override
        public Annotation next() {
            if (hasNext()) {
                nextCalled = true;
                return currentIterator.next();
            }
            return null;
        }

        @Override
        public void remove() {
            if (nextCalled) {
                currentIterator.remove();
            } else {
                throw new IllegalStateException();
            }
        }
    }

}
