// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.stream.Collectors;

public class WeakAnd extends QueryChain {

    private final Query value;
    private Annotation annotation;

    WeakAnd(Query value) {
        this.value = value;
        this.nonEmpty = true;
    }

    public WeakAnd annotate(Annotation annotation) {
        this.annotation = annotation;
        return this;
    }

    @Override
    public Select getSelect() {
        return sources.select;
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        // TODO: implementation
        throw new UnsupportedOperationException("method not implemented");
    }

    @Override
    public String toString() {
        boolean hasAnnotation = A.hasAnnotation(annotation);
        String
            s =
            Text.format("weakAnd(%s)",
                        value.queries.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return hasAnnotation ? Text.format("(%s%s)", annotation, s) : s;
    }

}