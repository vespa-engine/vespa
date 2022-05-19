// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.fieldoperation;

import com.yahoo.document.DataType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.util.Iterator;
import java.util.Set;

/**
 * @author Einar M R Rosenvinge
 */
public class SummaryInFieldLongOperation extends SummaryInFieldOperation {

    private DataType type;
    private Boolean bold;
    private Set<String> destinations = new java.util.LinkedHashSet<>();

    public SummaryInFieldLongOperation(String name) {
        super(name);
    }

    public SummaryInFieldLongOperation() {
        super(null);
    }

    public void setType(DataType type) {
        this.type = type;
    }

    public void setBold(Boolean bold) {
        this.bold = bold;
    }

    public void addDestination(String destination) {
        destinations.add(destination);
    }

    public Iterator<String> destinationIterator() {
        return destinations.iterator();
    }

    public void apply(SDField field) {
        if (type == null) {
            type = field.getDataType();
        }
        SummaryField summary = new SummaryField(name, type);
        applyToSummary(summary);
        field.addSummaryField(summary);
    }

    public void applyToSummary(SummaryField summary) {
        if (transform != null) {
            summary.setTransform(transform);
        }

        if (bold != null) {
            summary.setTransform(bold ? summary.getTransform().bold() : summary.getTransform().unbold());
        }

        for (SummaryField.Source source : sources) {
            summary.addSource(source);
        }

        for (String destination : destinations) {
            summary.addDestination(destination);
        }
    }
}
