// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.searchdefinition.DocumentReference;

/**
 * A field that is imported from a concrete field in a referenced document type and given an alias name.
 *
 * @author geirst
 */
public class ImportedField {

    private final String fieldName;
    private final DocumentReference reference;
    private final SDField targetField;

    public ImportedField(String fieldName,
                         DocumentReference reference,
                         SDField targetField) {
        this.fieldName = fieldName;
        this.reference = reference;
        this.targetField = targetField;
    }

    public String fieldName() {
        return fieldName;
    }

    public DocumentReference reference() {
        return reference;
    }

    public SDField targetField() {
        return targetField;
    }

}
