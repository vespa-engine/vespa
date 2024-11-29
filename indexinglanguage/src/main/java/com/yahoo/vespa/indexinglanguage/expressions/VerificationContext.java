// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class VerificationContext {

    private final Map<String, DataType> variables = new HashMap<>();
    private final FieldTypeAdapter fieldTypes;
    private DataType currentType;
    private String outputField;

    public VerificationContext() {
        this(null);
    }

    public VerificationContext(FieldTypeAdapter fieldTypes) {
        this.fieldTypes = fieldTypes;
    }

    public VerificationContext verify(Expression expression) {
        if (expression != null)
            expression.verify(this);
        return this;
    }

    /** Returns the type of the field processed by this. */
    public DataType getFieldType(Expression expression) {
        return fieldTypes.getInputType(expression, getOutputField());
    }

    /** Returns the type of the given field. */
    public DataType getFieldType(String fieldName, Expression expression) {
        return fieldTypes.getInputType(expression, fieldName);
    }

    public void tryOutputType(String fieldName, DataType valueType, Expression expression) {
        fieldTypes.tryOutputType(expression, fieldName, valueType);
    }

    /** Returns the current value type */
    public DataType getCurrentType() { return currentType; }

    /** Returns the current value type */
    public VerificationContext setCurrentType(DataType value) {
        this.currentType = value;
        return this;
    }

    public DataType getVariable(String name) { return variables.get(name); }

    public VerificationContext setVariable(String name, DataType value) {
        variables.put(name, value);
        return this;
    }

    /**
     * Returns the name of the (last) output field of the statement this is executed as a part of,
     * or null if none or not yet verified
     */
    public String getOutputField() { return outputField; }

    /** Sets the name of the (last) output field of the statement this is executed as a part of */
    public void setOutputField(String outputField) { this.outputField = outputField; }

    public VerificationContext clear() {
        variables.clear();
        currentType = null;
        return this;
    }

}

