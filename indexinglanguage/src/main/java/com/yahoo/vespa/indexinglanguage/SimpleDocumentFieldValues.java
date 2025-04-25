// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleDocumentFieldValues implements DocumentFieldValues {

    private final Document input;
    private final Document output;

    /** Creates an instance with empty output */
    public SimpleDocumentFieldValues(Document input) {
        this(input, new Document(input.getDataType(), input.getId()));
    }

    public SimpleDocumentFieldValues(Document input, Document output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public Document getFullOutput() {
        return output;
    }

    @Override
    public Document getUpdatableOutput() {
        return output;
    }

    @Override
    public DataType getFieldType(String fieldName, Expression exp) {
        try {
            return input.getDataType().buildFieldPath(fieldName).getResultingDataType();
        } catch (IllegalArgumentException e) {
            throw new VerificationException(exp, "Input field '" + fieldName + "' not found");
        }
    }

    @Override
    public FieldValue getInputValue(String fieldName) {
        try {
            return input.getRecursiveValue(fieldName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public FieldValue getInputValue(FieldPath fieldPath) {
        try {
            return input.getRecursiveValue(fieldPath);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public SimpleDocumentFieldValues setOutputValue(String fieldName, FieldValue fieldValue, Expression exp) {
        Field field = output.getField(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in document type '" +
                                               output.getDataType().getName());
        }
        output.setFieldValue(field, fieldValue);
        return this;
    }

    @Override
    public String toString() {
        return "document field values: " + output + " with " + output.getFieldCount() + " fields";
    }

}
