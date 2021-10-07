// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.util.Set;

/**
 * @author Einar M R Rosenvinge
 */
public class SummaryToOperation implements FieldOperation {

    private Set<String> destinations = new java.util.LinkedHashSet<>();
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public void addDestination(String destination) {
        destinations.add(destination);
    }

    public void apply(SDField field) {
        SummaryField summary;
        summary = field.getSummaryField(name);
        if (summary == null) {
            summary = new SummaryField(field);
            summary.addSource(field.getName());
            summary.addDestination("default");
            field.addSummaryField(summary);
        }
        summary.setImplicit(false);

        for (String destination : destinations) {
            summary.addDestination(destination);
        }
    }

}
