// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class TypeContext {

    private final Map<String, DataType> variableTypes = new HashMap<>();
    private final FieldTypes fieldTypes;

    public TypeContext(FieldTypes fieldTypes) {
        this.fieldTypes = Objects.requireNonNull(fieldTypes);
    }

    public TypeContext resolve(Expression expression) {
        if (expression != null)
            expression.resolve(this);
        return this;
    }

    /** Returns the type of the given field. */
    public DataType getFieldType(String fieldName, Expression expression) {
        return fieldTypes.getFieldType(fieldName, expression);
    }

    public DataType getVariableType(String name) { return variableTypes.get(name); }

    public TypeContext setVariableType(String name, DataType value) {
        variableTypes.put(name, value);
        return this;
    }

    public TypeContext clear() {
        variableTypes.clear();
        return this;
    }

}
