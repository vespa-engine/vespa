// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.collections.LazyMap;
import com.yahoo.document.DocumentId;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.detect.Detection;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Simon Thoresen Hult
 */
public class ExecutionContext {

    private final Map<String, FieldValue> variables = new HashMap<>();
    private final FieldValues fieldValues;
    private FieldValue currentValue;
    private Language language;
    private final Map<Object, Object> cache = LazyMap.newHashMap();
    // Document id is practical for logging and informative error messages
    private DocumentId documentId;
    private boolean isReindexingOperation;

    public ExecutionContext() {
        this(null);
    }

    public ExecutionContext(FieldValues fieldValue) {
        this.fieldValues = fieldValue;
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
        return fieldValues != null && fieldValues.isComplete();
    }

    public FieldValue getFieldValue(String fieldName) {
        return fieldValues.getInputValue(fieldName);
    }

    public FieldValue getFieldValue(FieldPath fieldPath) {
        return fieldValues.getInputValue(fieldPath);
    }

    public ExecutionContext setFieldValue(String fieldName, FieldValue fieldValue, Expression expression) {
        this.fieldValues.setOutputValue(fieldName, fieldValue, expression);
        return this;
    }

    public FieldValues getFieldValues() { return fieldValues; }

    public FieldValue getVariable(String name) {
        return variables.get(name);
    }

    public ExecutionContext setVariable(String name, FieldValue value) {
        variables.put(name, value);
        return this;
    }

    public FieldValue getCurrentValue() { return currentValue; }

    public ExecutionContext setCurrentValue(FieldValue value) {
        this.currentValue = value;
        return this;
    }

    /** Returns a cached value, or null if not present. */
    public Object getCachedValue(Object key) {
        return cache.get(key);
    }

    public void putCachedValue(String key, Object value) {
        cache.put(key, value);
    }

    /** Returns a mutable reference to the cache of this. */
    public Map<Object, Object> getCache() {
        return cache;
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

    public boolean isReindexingOperation() { return isReindexingOperation; }
    public ExecutionContext setReindexingOperation() { isReindexingOperation = true; return this; }

    public Optional<DocumentId> getDocumentId() { return Optional.ofNullable(documentId); }
    public ExecutionContext setDocumentId(DocumentId id) { documentId = Objects.requireNonNull(id); return this; }

    /** Clears all state in this except the cache. */
    public ExecutionContext clear() {
        // Why is language not cleared?
        variables.clear();
        currentValue = null;
        documentId = null;
        isReindexingOperation = false;
        return this;
    }

}
