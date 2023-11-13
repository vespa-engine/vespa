// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import java.util.IdentityHashMap;
import java.util.Iterator;

/**
 * @author Einar M R Rosenvinge
 */
abstract class IteratingAnnotationContainer extends AnnotationContainer {

    @Override
        Iterator<Annotation> iterator(SpanNode node) {
        IdentityHashMap<SpanNode, SpanNode> nodes = new IdentityHashMap<>();
        nodes.put(node, node);
        return iterator(nodes);
    }

    @Override
    Iterator<Annotation> iteratorRecursive(SpanNode node) {
        IdentityHashMap<SpanNode, SpanNode> nodes = new IdentityHashMap<>();
        nodes.put(node, node);
        Iterator<SpanNode> childrenIt = node.childIteratorRecursive();
        while (childrenIt.hasNext()) {
            SpanNode child = childrenIt.next();
            nodes.put(child, child);
        }
        return iterator(nodes);
    }

    abstract Iterator<Annotation> iterator(IdentityHashMap<SpanNode, SpanNode> nodes);

}
