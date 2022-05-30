// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.vespa.documentmodel.SummaryTransform;

/**
 * The result transformation of a named field
 *
 * @author bratseth
 */
public class FieldResultTransform {

    private final String fieldName;

    private SummaryTransform transform;

    private final String argument;

    public FieldResultTransform(String fieldName, SummaryTransform transform, String argument) {
        this.fieldName = fieldName;
        this.transform = transform;
        this.argument = argument;
    }

    public String getFieldName() { return fieldName; }

    public SummaryTransform getTransform() { return transform; }

    public void setTransform(SummaryTransform transform) { this.transform = transform; }

    /** Returns the argument of this (used as input to the backend docsum rewriter) */
    public String getArgument() { return argument; }

    public int hashCode() {
        return fieldName.hashCode() + 11 * transform.hashCode() + 17 * argument.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof FieldResultTransform)) return false;
        FieldResultTransform other = (FieldResultTransform)o;

        return
            this.fieldName.equals(other.fieldName) &&
            this.transform.equals(other.transform) &&
            this.argument.equals(other.argument);
    }

    @Override
    public String toString() {
        String sourceString = "";
        if ( ! argument.equals(fieldName))
            sourceString = " (argument: " + argument + ")";
        return "field " + fieldName + ": " + transform + sourceString;
    }

}
