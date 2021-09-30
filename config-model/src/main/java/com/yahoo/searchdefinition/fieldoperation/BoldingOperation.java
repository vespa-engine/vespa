// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;

/**
 * @author Einar M R Rosenvinge
 */
public class BoldingOperation implements FieldOperation {

    private final boolean bold;

    public BoldingOperation(boolean bold) {
        this.bold = bold;
    }

    public void apply(SDField field) {
        SummaryField summaryField = field.getSummaryField(field.getName(), true);
        summaryField.addSource(field.getName());
        summaryField.addDestination("default");
        summaryField.setTransform(bold ? summaryField.getTransform().bold() : summaryField.getTransform().unbold());
    }

}
