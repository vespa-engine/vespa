// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.schema.DocumentReference;

import java.util.Collection;
import java.util.Map;

/**
 * A complex field that is imported from a concrete field in a referenced document type and given an alias name.
 */
public class ImportedComplexField extends ImportedField {

    private Map<String, ImportedField> nestedFields;

    public ImportedComplexField(String fieldName, DocumentReference reference, ImmutableSDField targetField) {
        super(fieldName, reference, targetField);
        nestedFields = new java.util.LinkedHashMap<>(0);
    }

    @Override
    public ImmutableSDField asImmutableSDField() {
        return new ImmutableImportedComplexSDField(this);
    }

    public void addNestedField(ImportedField importedField) {
        String prefix = fieldName() + ".";
        assert(importedField.fieldName().substring(0, prefix.length()).equals(prefix));
        String suffix = importedField.fieldName().substring(prefix.length());
        nestedFields.put(suffix, importedField);
    }

    public Collection<ImportedField> getNestedFields() {
        return nestedFields.values();
    }

    public ImportedField getNestedField(String name) {
        if (name.contains(".")) {
            String superFieldName = name.substring(0,name.indexOf("."));
            String subFieldName = name.substring(name.indexOf(".")+1);
            ImportedField superField = nestedFields.get(superFieldName);
            if (superField != null && superField instanceof ImportedComplexField) {
                return ((ImportedComplexField)superField).getNestedField(subFieldName);
            }
            return null;
        }
        return nestedFields.get(name);
    }
}
