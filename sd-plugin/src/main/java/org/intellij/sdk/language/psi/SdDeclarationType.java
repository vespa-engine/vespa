// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.psi;

/**
 * This Enum describes the different declarations' types and their names.
 * @author shahariel
 */
public enum SdDeclarationType {
    DOCUMENT("Document"),
    STRUCT("Struct"),
    ANNOTATION("Annotation"),
    SCHEMA_FIELD("Field (in Schema)"),
    DOCUMENT_FIELD("Field (in Document)"),
    STRUCT_FIELD("Struct-Field"),
    ANNOTATION_FIELD("Field (in Annotation)"),
    DOCUMENT_STRUCT_FIELD("Field (in Struct)"),
    IMPORTED_FIELD("Imported Field"),
    DOCUMENT_SUMMARY("Document-Summary"),
    RANK_PROFILE("Rank Profile"),
    MACRO("Macro"),
    MACRO_ARGUMENT("Macro's Argument"),
    QUERY("Query (first use in file)"),
    ITEM_RAW_SCORE("ItemRawScore (first use in file)");
    
    private final String typeName;
    SdDeclarationType(String name) {
        this.typeName = name;
    }
    
    @Override
    public String toString() {
        return typeName;
    }
}
