package com.yahoo.searchdefinition.document;

/**
 * Temporary reference to a field in another document type.
 *
 * After all SD files are parsed this temporary reference can be validated and connected
 * to the actual field instance in the referenced document type.
 *
 * @author geirst
 */
public class TemporaryFieldReference {

    private final String refFieldName;
    private final String fieldNameInRefType;

    /**
     * @param refFieldName points to a field of type reference (in this document type).
     * @param fieldNameInRefType points to a field in the referenced document type.
     */
    public TemporaryFieldReference(String refFieldName, String fieldNameInRefType) {
        this.refFieldName = refFieldName;
        this.fieldNameInRefType = fieldNameInRefType;
    }

    public String refFieldName() {
        return refFieldName;
    }

    public String fieldNameInRefType() {
        return fieldNameInRefType;
    }
}
