// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.schema.Schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A set of fields that are imported from referenced document types.
 *
 * This is temporary AST structure that only refers to the imported fields by name.
 *
 * @author geirst
 */
public class TemporaryImportedFields {

    private final Schema owner;
    private final Map<String, TemporaryImportedField> fields = new LinkedHashMap<>();

    public TemporaryImportedFields(Schema owner) {
        this.owner = owner;
    }

    public void add(TemporaryImportedField importedField) {
        fields.put(importedField.fieldName(), importedField);
    }

    public boolean hasField(String fieldName) {
        return fields.get(fieldName) != null;
    }

    public Map<String, TemporaryImportedField> fields() {
        if (owner.inherited().isEmpty()) return Collections.unmodifiableMap(fields);
        if (owner.inherited().get().temporaryImportedFields().isEmpty()) return Collections.unmodifiableMap(fields);

        var allFields = new HashMap<>(owner.inherited().get().temporaryImportedFields().get().fields());
        allFields.putAll(fields);
        return allFields;
    }

}
