// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.SDField;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author Einar M R Rosenvinge
 */
public class StructFieldOperation implements FieldOperation, FieldOperationContainer {

    private final String structFieldName;
    private final List<FieldOperation> pendingOperations = new LinkedList<>();

    public StructFieldOperation(String structFieldName) {
        this.structFieldName = structFieldName;
    }

    public void apply(SDField field) {
        SDField structField = field.getStructField(structFieldName);
        if (structField == null ) {
            throw new IllegalArgumentException("Struct field '" + structFieldName + "' has not been defined in struct " +
                                               "for field '" + field.getName() + "'.");
        }

        applyOperations(structField);
    }

    @Override
    public void addOperation(FieldOperation op) {
        pendingOperations.add(op);
    }

    @Override
    public void applyOperations(SDField field) {
        if (pendingOperations.isEmpty()) return;

        Collections.sort(pendingOperations);
        ListIterator<FieldOperation> ops = pendingOperations.listIterator();
        while (ops.hasNext()) {
            FieldOperation op = ops.next();
            ops.remove();
            op.apply(field);
        }
    }

    @Override
    public String getName() {
        return structFieldName;
    }

}
