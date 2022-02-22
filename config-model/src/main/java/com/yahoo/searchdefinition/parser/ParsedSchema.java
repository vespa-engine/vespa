// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.document.Stemming;

import java.util.ArrayList;
import java.util.List;

public class ParsedSchema {
    private final String name;
    private final List<String> inherited = new ArrayList<>();

    public  ParsedSchema(String name) {
        this.name = name;
    }

    String getName() { return name; }

    void addDocument(ParsedDocument document) {}
    void addImportedField(String asFieldName, String refFieldName, String foregnFieldName) {}
    void addOnnxModel(OnnxModel model) {}
    void addRankingConstant(RankingConstant constant) {}
    void enableRawAsBase64(boolean value) {}
    void inherit(String other) { inherited.add(other); }
    void setStemming(Stemming value) {}

    /*
    private final List<ParsedField> fields = new ArrayList<>();
    List<ParsedField> getFields() { return fields; }
    void addField(ParsedField field) { fields.add(field); }
    void addAnnotation(ParsedAnnotation annotation) {}
    void addIndex(ParsedIndex index) {}
    void addStruct(ParsedStruct struct) {}
    void addFieldSet(ParsedFieldSet fieldSet) {}
    void addDocumentSummary(ParsedDocumentSummary docsum) {}
    void addRankProfile(ParsedRankProfile profile) {}
    */
}

