// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.collections.LazyMap;
import com.yahoo.document.DataType;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detection;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public class ExecutionContext {

    private final Map<String, FieldValue> variables = new HashMap<>();
    private final FieldValueAdapter fieldValue;
    private FieldValue currentValue;
    private Language language;
    private final Map<Object, Object> cache = LazyMap.newHashMap();

    public ExecutionContext() {
        this(null);
    }

    public ExecutionContext(FieldValueAdapter fieldValue) {
        this.fieldValue = fieldValue;
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
    public boolean isComplete() {
        return fieldValue != null && fieldValue.isComplete();
    }

    public DataType getInputType(Expression exp, String fieldName) {
        return fieldValue.getInputType(exp, fieldName);
    }

    public FieldValue getInputValue(String fieldName) {
        return fieldValue.getInputValue(fieldName);
    }

    public FieldValue getInputValue(FieldPath fieldPath) {
        return fieldValue.getInputValue(fieldPath);
    }

    public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
        fieldValue.tryOutputType(exp, fieldName, valueType);
    }

    public ExecutionContext setOutputValue(Expression exp, String fieldName, FieldValue fieldValue) {
        this.fieldValue.setOutputValue(exp, fieldName, fieldValue);
        return this;
    }

    public FieldValueAdapter getFieldValue() {
        return fieldValue;
    }

    public FieldValue getVariable(String name) {
        return variables.get(name);
    }

    public ExecutionContext setVariable(String name, FieldValue value) {
        variables.put(name, value);
        return this;
    }

    public Language getLanguage() { return language; }

    public ExecutionContext setLanguage(Language language) {
        this.language = Objects.requireNonNull(language);
        return this;
    }

    public Language resolveLanguage(Linguistics linguistics) {
        if (language != Language.UNKNOWN) return language;
        if (linguistics == null) return Language.ENGLISH;

        Detection detection = linguistics.getDetector().detect(String.valueOf(currentValue), null);
        if (detection == null) return Language.ENGLISH;

        Language detected = detection.getLanguage();
        if (detected == Language.UNKNOWN) return Language.ENGLISH;
        return detected;
    }

    public FieldValue getValue() { return currentValue; }

    public ExecutionContext setValue(FieldValue value) {
        this.currentValue = value;
        return this;
    }

    public void putCachedValue(String key, Object value) {
        cache.put(key, value);
    }

    /** Returns a cached value, or null if not present. */
    public Object getCachedValue(Object key) {
        return cache.get(key);
    }

    /** Returns a mutable reference to the cache of this. */
    public Map<Object, Object> getCache() {
        return cache;
    }

    /** Clears all state in this except the cache. */
    public ExecutionContext clear() {
        variables.clear();
        currentValue = null;
        return this;
    }

    void fillVariableTypes(VerificationContext vctx) {
        for (var entry : variables.entrySet()) {
            vctx.setVariable(entry.getKey(), entry.getValue().getDataType());
        }
    }

}
