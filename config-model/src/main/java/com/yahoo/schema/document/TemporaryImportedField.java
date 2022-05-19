// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

/**
 * A field that is imported from a field in a referenced document type and given an alias name.
 *
 * This is temporary AST structure that only refers to the imported field by name.
 *
 * @author geirst
 */
public class TemporaryImportedField {

    private final String fieldName;
    private final String referenceFieldName;
    private final String targetFieldName;

    public TemporaryImportedField(String fieldName,
                                  String referenceFieldName,
                                  String targetFieldName) {
        this.fieldName = fieldName;
        this.referenceFieldName = referenceFieldName;
        this.targetFieldName = targetFieldName;
    }

    public String fieldName() {
        return fieldName;
    }

    public String referenceFieldName() {
        return referenceFieldName;
    }

    public String targetFieldName() {
        return targetFieldName;
    }

}
