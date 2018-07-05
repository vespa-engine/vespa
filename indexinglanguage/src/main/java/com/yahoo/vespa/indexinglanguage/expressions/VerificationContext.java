// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class VerificationContext implements FieldTypeAdapter, Cloneable {

    private final Map<String, DataType> variables = new HashMap<String, DataType>();
    private final FieldTypeAdapter adapter;
    private DataType value;

    public VerificationContext() {
        this.adapter = null;
    }

    public VerificationContext(FieldTypeAdapter adapter) {
        this.adapter = adapter;
    }

    public VerificationContext execute(Expression exp) {
        if (exp != null) {
            exp.verify(this);
        }
        return this;
    }

    @Override
    public DataType getInputType(Expression exp, String fieldName) {
        return adapter.getInputType(exp, fieldName);
    }

    @Override
    public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
        adapter.tryOutputType(exp, fieldName, valueType);
    }

    public DataType getVariable(String name) {
        return variables.get(name);
    }

    public VerificationContext setVariable(String name, DataType value) {
        variables.put(name, value);
        return this;
    }

    public DataType getValue() {
        return value;
    }

    public VerificationContext setValue(DataType value) {
        this.value = value;
        return this;
    }

    public VerificationContext clear() {
        variables.clear();
        value = null;
        return this;
    }
}

