// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.searchdefinition.DocumentReference;

/**
 * A field that is imported from a concrete field in a referenced document type and given an alias name.
 *
 * @author geirst
 */
public class ImportedField {

    private final String aliasFieldName;
    private final DocumentReference documentReference;
    private final SDField referencedField;

    public ImportedField(String aliasFieldName,
                         DocumentReference documentReference,
                         SDField referencedField) {
        this.aliasFieldName = aliasFieldName;
        this.documentReference = documentReference;
        this.referencedField = referencedField;
    }

    public String aliasFieldName() {
        return aliasFieldName;
    }

    public DocumentReference documentReference() {
        return documentReference;
    }

    public SDField referencedField() {
        return referencedField;
    }

}
