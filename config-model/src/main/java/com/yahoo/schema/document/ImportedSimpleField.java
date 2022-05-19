// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.schema.DocumentReference;

/**
 * A simple field that is imported from a concrete field in a referenced document type and given an alias name.
 */
public class ImportedSimpleField extends ImportedField {
    public ImportedSimpleField(String fieldName, DocumentReference reference, ImmutableSDField targetField) {
        super(fieldName, reference, targetField);
    }

    @Override
    public ImmutableSDField asImmutableSDField() {
        return new ImmutableImportedSDField(this);
    }
}
