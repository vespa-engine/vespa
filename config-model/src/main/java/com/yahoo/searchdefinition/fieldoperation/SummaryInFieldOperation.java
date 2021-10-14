// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;

import java.util.Set;

/**
 * @author Einar M R Rosenvinge
 */
public abstract class SummaryInFieldOperation implements FieldOperation {

    protected String name;
    protected SummaryTransform transform;
    protected Set<SummaryField.Source> sources = new java.util.LinkedHashSet<>();

    public SummaryInFieldOperation(String name) {
        this.name = name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setTransform(SummaryTransform transform) {
        this.transform = transform;
    }

    public SummaryTransform getTransform() {
        return transform;
    }

    public void addSource(String name) {
        sources.add(new SummaryField.Source(name));
    }

    public void addSource(SummaryField.Source source) {
        sources.add(source);
    }

}
