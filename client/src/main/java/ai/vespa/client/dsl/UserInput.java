// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.UUID;

public class UserInput extends QueryChain {

    private final Annotation annotation; // accept only defaultIndex annotation
    private final String value;
    private final boolean valueIsReference;
    private final String indexField;
    private boolean setDefaultIndex;

    UserInput(Sources sources, String value) {
        this(sources, A.empty(), value);
    }

    UserInput(Sources sources, Annotation annotation, String value) {
        this.sources = sources;
        this.annotation = annotation;
        this.value = value;
        this.valueIsReference = value.startsWith("@");
        this.nonEmpty = true;

        if (annotation.contains("defaultIndex")) {
            setDefaultIndex = true;
            indexField = (String) annotation.get("defaultIndex");
        } else {
            indexField = UUID.randomUUID().toString().substring(0, 5);
        }
    }

    UserInput(String value) {
        this(A.empty(), value);
    }

    UserInput(Annotation annotation, String value) {
        this(null, annotation, value);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        if (setDefaultIndex)
            b.append("(").append(annotation);
        b.append("userInput(");
        if ( ! valueIsReference)
            b.append("\"");
        b.append(value);
        if ( ! valueIsReference)
            b.append("\"");
        b.append(")");
        if (setDefaultIndex)
            b.append(")");
        return b.toString();
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        return !"andnot".equals(this.op) && this.indexField.equals(fieldName);
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        return hasPositiveSearchField(fieldName) && this.value.equals(value);
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        return "andnot".equals(this.op) && this.indexField.equals(fieldName);
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        return hasNegativeSearchField(fieldName) && this.value.equals(value);
    }

}
