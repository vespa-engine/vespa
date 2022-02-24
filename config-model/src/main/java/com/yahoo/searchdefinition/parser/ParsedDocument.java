// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class holds the extracted information after parsing a
 * "document" block in a schema (.sd) file, using simple data
 * structures as far as possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
public class ParsedDocument {
    private final String name;
    private final List<String> inherited = new ArrayList<>();
    private final Map<String, ParsedField> docFields = new HashMap<>();
    private final Map<String, ParsedStruct> docStructs = new HashMap<>();
    private final Map<String, ParsedAnnotation> docAnnotations = new HashMap<>();

    public ParsedDocument(String name) {
        this.name = name;
    }

    String name() { return name; }
    List<String> getInherited() { return ImmutableList.copyOf(inherited); }
    List<ParsedAnnotation> getAnnotations() { return ImmutableList.copyOf(docAnnotations.values()); }
    List<ParsedField> getFields() { return ImmutableList.copyOf(docFields.values()); }
    List<ParsedStruct> getStructs() { return ImmutableList.copyOf(docStructs.values()); }

    void inherit(String other) { inherited.add(other); }

    void addField(ParsedField field) {
        String fieldName = field.name();
        if (docFields.containsKey(fieldName)) {
            throw new IllegalArgumentException("document "+this.name+" already has field "+fieldName);
        }
        docFields.put(fieldName, field);
    }

    void addStruct(ParsedStruct struct) {
        String sName = struct.name();
        if (docStructs.containsKey(sName)) {
            throw new IllegalArgumentException("document "+this.name+" already has struct "+sName);
        }
        docStructs.put(sName, struct);
    }

    void addAnnotation(ParsedAnnotation annotation) {
        String annName = annotation.name();
        if (docAnnotations.containsKey(annName)) {
            throw new IllegalArgumentException("document "+this.name+" already has annotation "+annName);
        }
        docAnnotations.put(annName, annotation);
    }

}

