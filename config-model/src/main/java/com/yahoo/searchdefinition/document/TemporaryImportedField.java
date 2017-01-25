// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

/**
 * A field that is imported from a field in a referenced document type and given an alias name.
 *
 * This is temporary AST structure that only refers to the imported field by name.
 *
 * @author geirst
 */
public class TemporaryImportedField {

    private final String aliasFieldName;
    private final String documentReferenceFieldName;
    private final String foreignFieldName;

    public TemporaryImportedField(String aliasFieldName,
                                  String documentReferenceFieldName,
                                  String foreignFieldName) {
        this.aliasFieldName = aliasFieldName;
        this.documentReferenceFieldName = documentReferenceFieldName;
        this.foreignFieldName = foreignFieldName;
    }

    public String aliasFieldName() {
        return aliasFieldName;
    }

    public String documentReferenceFieldName() {
        return documentReferenceFieldName;
    }

    public String foreignFieldName() {
        return foreignFieldName;
    }

}
