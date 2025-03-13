// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class VerificationContext {

    private final Map<String, DataType> variables = new HashMap<>();
    private final FieldTypeAdapter fieldTypes;

    public VerificationContext(FieldTypeAdapter fieldTypes) {
        this.fieldTypes = Objects.requireNonNull(fieldTypes);
    }

    public VerificationContext verify(Expression expression) {
        if (expression != null)
            expression.verify(this);
        return this;
    }

    /** Returns the type of the given field. */
    public DataType getFieldType(String fieldName, Expression expression) {
        return fieldTypes.getInputType(expression, fieldName);
    }

    public DataType getVariable(String name) { return variables.get(name); }

    public VerificationContext setVariable(String name, DataType value) {
        variables.put(name, value);
        return this;
    }

    public VerificationContext clear() {
        variables.clear();
        return this;
    }

}
