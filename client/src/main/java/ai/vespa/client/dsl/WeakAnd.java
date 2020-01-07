// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.stream.Collectors;

public class WeakAnd extends QueryChain {

    private String fieldName;
    private Annotation annotation;
    private Query value;


    WeakAnd(String fieldName, Query value) {
        this.fieldName = fieldName;
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
        return false;
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        // TODO: implementation
        return false;
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        // TODO: implementation
        return false;
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        // TODO: implementation
        return false;
    }

    @Override
    public String toString() {
        boolean hasAnnotation = A.hasAnnotation(annotation);
        String
            s =
            String.format("weakAnd(%s, %s)", fieldName,
                          value.queries.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return hasAnnotation ? String.format("([%s]%s)", annotation, s) : s;
    }
}

