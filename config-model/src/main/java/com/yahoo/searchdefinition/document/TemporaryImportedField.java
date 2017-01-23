package com.yahoo.searchdefinition.document;

/**
 * Temporary field that is imported from a field in a referenced document type.
 *
 * After all SD files are parsed this temporary field can be validated and connected
 * to the actual field instance in the referenced document type.
 *
 * @author geirst
 */
public class TemporaryImportedField {

    private final String fieldName;
    private final TemporaryFieldReference reference;

    public TemporaryImportedField(String fieldName, TemporaryFieldReference reference) {
        this.fieldName = fieldName;
        this.reference = reference;
    }

    public String fieldName() {
        return fieldName;
    }

    public TemporaryFieldReference reference() {
        return reference;
    }
}
