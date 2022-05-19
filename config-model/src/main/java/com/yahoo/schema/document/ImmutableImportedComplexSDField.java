// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import java.util.Collection;

import static java.util.stream.Collectors.toList;

/**
 * Wraps {@link ImportedComplexField} as {@link ImmutableSDField}.
 */
public class ImmutableImportedComplexSDField extends ImmutableImportedSDField {
    private final ImportedComplexField importedComplexField;

    public ImmutableImportedComplexSDField(ImportedComplexField importedField) {
        super(importedField);
        importedComplexField = importedField;
    }

    @Override
    public ImmutableSDField getStructField(String name) {
        ImportedField field = importedComplexField.getNestedField(name);
        return (field != null) ? field.asImmutableSDField() : null;
    }

    @Override
    public Collection<? extends ImmutableSDField> getStructFields() {
        return importedComplexField.getNestedFields().stream().map(field -> field.asImmutableSDField()).collect(toList());
    }
}
