// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.collections.LazyMap;
import com.yahoo.document.DocumentId;
import com.yahoo.document.FieldPath;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;


import java.time.Instant;
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
    private Language assignedLanguage = Language.UNKNOWN;
    private Language detectedLanguage = Language.UNKNOWN;
    private final Map<Object, Object> cache = LazyMap.newHashMap();
    // Document id is practical for logging and informative error messages
    private DocumentId documentId;
    private boolean isReindexingOperation = false;
    private Instant deadline;

    public ExecutionContext() {
        this(null);
    }

    public ExecutionContext(FieldValues fieldValue) {
        this.fieldValues = fieldValue;
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

    /** Returns the explicitly set or last detected language. Returns UNKNOWN if neither set nor detected. */
    public Language getLanguage() {
        if (assignedLanguage != Language.UNKNOWN) return assignedLanguage;
        return detectedLanguage;
    }

    public ExecutionContext setLanguage(Language language) {
        this.assignedLanguage = Objects.requireNonNull(language);
        return this;
    }

    public Language resolveLanguage(Linguistics linguistics) {
        if (assignedLanguage != Language.UNKNOWN) return assignedLanguage;
        if (detectedLanguage != Language.UNKNOWN) return detectedLanguage;
        if (linguistics == null) return Language.ENGLISH;
        detectedLanguage = detectLanguage(linguistics);
        return detectedLanguage;
    }

    // Caching the result as language detection is expensive
    private Language detectLanguage(Linguistics linguistics) {
        record DetectedLanguageCacheKey(String text) {}
        var text = String.valueOf(currentValue);
        var cacheKey = new DetectedLanguageCacheKey(text);
        if (cache.get(cacheKey) instanceof Language cached) return cached;
        var detection = linguistics.getDetector().detect(text, null);
        if (detection == null) return Language.ENGLISH;
        var language = detection.getLanguage();
        if (language == Language.UNKNOWN) language = Language.ENGLISH;
        cache.put(cacheKey, language);
        return language;
    }

    public boolean isReindexingOperation() { return isReindexingOperation; }
    public ExecutionContext setReindexingOperation() { isReindexingOperation = true; return this; }

    public Optional<DocumentId> getDocumentId() { return Optional.ofNullable(documentId); }
    public ExecutionContext setDocumentId(DocumentId id) { documentId = Objects.requireNonNull(id); return this; }

    public Optional<Instant> getDeadline() { return Optional.ofNullable(deadline); }
    public ExecutionContext setDeadline(Instant deadline) { this.deadline = deadline; return this; }

    /**
     * Clears all state in this pertaining to the current indexing statement
     * Does not clear the cache.
     * Note that assignLanguage is not cleared; an indexing statement doing
     * set_language should affect the following statements.
     */
    public ExecutionContext clear() {
        // We do not really want to clear variables here, but because
        // indexing statements are re-ordered letting them survive
        // will be even more confusing than clearing them.
        variables.clear();
        detectedLanguage = Language.UNKNOWN;
        currentValue = null;
        // note: must not reset per-document or global values (like isReindexingOperation, deadline)
        return this;
    }

}
