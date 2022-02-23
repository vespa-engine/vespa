// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.document.Stemming;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the extracted information after parsing
 * one schema (.sd) file, using simple data structures
 * as far as possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
public class ParsedSchema {
    private final String name;
    private final List<String> inherited = new ArrayList<>();

    public  ParsedSchema(String name) {
        this.name = name;
    }

    String name() { return name; }

    void addAnnotation(ParsedAnnotation annotation) {}
    void addDocument(ParsedDocument document) {}
    void addDocumentSummary(ParsedDocumentSummary docsum) {}
    void addField(ParsedField field) {}
    void addFieldSet(ParsedFieldSet fieldSet) {}
    void addImportedField(String asFieldName, String refFieldName, String foregnFieldName) {}
    void addIndex(ParsedIndex index) {}
    void addOnnxModel(OnnxModel model) {}
    void addRankProfile(ParsedRankProfile profile) {}
    void addRankingConstant(RankingConstant constant) {}
    void addStruct(ParsedStruct struct) {}
    void enableRawAsBase64(boolean value) {}
    void inherit(String other) { inherited.add(other); }
    void setStemming(Stemming value) {}

    /*
    private final List<ParsedField> fields = new ArrayList<>();
    List<ParsedField> getFields() { return fields; }
    */
}

