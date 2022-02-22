// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import java.util.ArrayList;
import java.util.List;

public class ParsedSchema {
    private final String name;
    private final List<String> inherited = new ArrayList<>();

    public  ParsedSchema(String name) {
        this.name = name;
    }

    String getName() { return name; }
    void inherit(String other) { inherited.add(other); }
    void addDocument(ParsedDocument document) {}

    /*
    private final List<ParsedField> fields = new ArrayList<>();
    List<ParsedField> getFields() { return fields; }
    void addField(ParsedField field) { fields.add(field); }
    void addOnnxModel(Object model) {}
    void addImportedField(String asFieldName, String refFieldName, String foregnFieldName) {}
    void addAnnotation(ParsedAnnotation annotation) {}
    void addIndex(ParsedIndex index) {}
    void enableRawAsBase64(boolean value) {}
    void addStruct(ParsedStruct struct) {}
    void setStemming(String value) {}
    void addRankingConstant(Object constant) {}
    void addFieldSet(ParsedFieldSet fieldSet) {}
    void addDocumentSummary(ParsedDocumentSummary docsum) {}
    void addRankProfile(ParsedRankProfile profile) {}
    */
}

