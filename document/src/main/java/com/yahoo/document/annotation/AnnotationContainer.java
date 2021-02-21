// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author Einar M R Rosenvinge
 */
abstract class AnnotationContainer {

    /**
     * Adds all annotations of the given collection to this container.
     *
     * @param annotations the annotations to add.
     */
    abstract void annotateAll(Collection<Annotation> annotations);

    /**
     * Adds an annotation to this container.
     *
     * @param annotation the annotation to add.
     */
    abstract void annotate(Annotation annotation);

    /**
     * Returns a mutable collection of annotations.
     *
     * @return a mutable collection of annotations.
     */
    abstract Collection<Annotation> annotations();

    /**
     * Returns an Iterator over all annotations that annotate the given node.
     *
     * @param node the node to return annotations for.
     * @return an Iterator over all annotations that annotate the given node.
     */
    abstract Iterator<Annotation> iterator(SpanNode node);

    /**
     * Returns a recursive Iterator over all annotations that annotate the given node and its subnodes.
     *
     * @param node the node to recursively return annotations for.
     * @return a recursive Iterator over all annotations that annotate the given node and its subnodes.
     */
    abstract Iterator<Annotation> iteratorRecursive(SpanNode node);

    // TODO: remember equals and hashcode in subclasses!

}
