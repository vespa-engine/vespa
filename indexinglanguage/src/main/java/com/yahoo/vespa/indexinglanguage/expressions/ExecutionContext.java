// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detection;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class ExecutionContext implements FieldTypeAdapter, FieldValueAdapter, Cloneable {

    private final Map<String, FieldValue> variables = new HashMap<>();
    private final FieldValueAdapter adapter;
    private FieldValue value;
    private Language language;

    public ExecutionContext() {
        this(null);
    }

    public ExecutionContext(FieldValueAdapter adapter) {
        this.adapter = adapter;
        this.language = Language.UNKNOWN;
    }

    public ExecutionContext execute(Expression expression) {
        if (expression != null)
            expression.execute(this);
        return this;
    }

    /**
     * Returns whether this is for a complete execution of all statements of a script,
     * or a partial execution of only the statements accessing the available data.
     */
    public boolean isComplete() { return adapter == null ? false : adapter.isComplete(); }

    @Override
    public DataType getInputType(Expression exp, String fieldName) {
        return adapter.getInputType(exp, fieldName);
    }

    @Override
    public FieldValue getInputValue(String fieldName) {
        if (adapter == null) {
            throw new IllegalStateException("Can not get field '" + fieldName + "' because adapter is null.");
        }
        return adapter.getInputValue(fieldName);
    }

    @Override
    public FieldValue getInputValue(FieldPath fieldPath) {
        if (adapter == null) {
            throw new IllegalStateException("Can not get field '" + fieldPath + "' because adapter is null.");
        }
        return adapter.getInputValue(fieldPath);
    }

    @Override
    public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
        adapter.tryOutputType(exp, fieldName, valueType);
    }

    @Override
    public ExecutionContext setOutputValue(Expression exp, String fieldName, FieldValue fieldValue) {
        if (adapter == null)
            throw new IllegalStateException("Can not set field '" + fieldName + "' because adapter is null.");
        adapter.setOutputValue(exp, fieldName, fieldValue);
        return this;
    }

    public FieldValueAdapter getAdapter() {
        return adapter;
    }

    public FieldValue getVariable(String name) {
        return variables.get(name);
    }

    public ExecutionContext setVariable(String name, FieldValue value) {
        variables.put(name, value);
        return this;
    }

    public Language getLanguage() {
        return language;
    }

    public ExecutionContext setLanguage(Language language) {
        language.getClass();
        this.language = language;
        return this;
    }

    public Language resolveLanguage(Linguistics linguistics) {
        if (language != null && language != Language.UNKNOWN) {
            return language;
        }
        if (linguistics == null) {
            return Language.ENGLISH;
        }
        Detection detection = linguistics.getDetector().detect(String.valueOf(value), null);
        if (detection == null) {
            return Language.ENGLISH;
        }
        Language detected = detection.getLanguage();
        if (detected == Language.UNKNOWN) {
            return Language.ENGLISH;
        }
        return detected;
    }

    public FieldValue getValue() {
        return value;
    }

    public ExecutionContext setValue(FieldValue value) {
        this.value = value;
        return this;
    }

    public ExecutionContext clear() {
        variables.clear();
        value = null;
        return this;
    }

}
