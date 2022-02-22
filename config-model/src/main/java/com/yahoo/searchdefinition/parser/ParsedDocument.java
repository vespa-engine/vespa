// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import java.util.ArrayList;
import java.util.List;

public class ParsedDocument {
    private final String name;
    private final List<String> inherited = new ArrayList<>();

    public  ParsedDocument(String name) {
        this.name = name;
    }

    String getName() { return name; }
    void inherit(String other) { inherited.add(other); }

    /*
    private final List<ParsedField> fields = new ArrayList<>();
    List<ParsedField> getFields() { return fields; }
    void addField(ParsedField field) { fields.add(field); }
    void addStruct(ParsedStruct type) {}
    void addAnnotation(ParsedAnnotation type) {}
    */
}

