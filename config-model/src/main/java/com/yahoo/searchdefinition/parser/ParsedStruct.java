// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the extracted information after parsing a "struct"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
public class ParsedStruct {
    private final String name;
    private final List<String> inherited = new ArrayList<>();
    private final List<ParsedField> fields = new ArrayList<>();

    public  ParsedStruct(String name) {
        this.name = name;
    }

    /* TODO make immutable */
    List<ParsedField> getFields() { return fields; }

    void inherit(String other) { inherited.add(other); }
    void addField(ParsedField field) { fields.add(field); }
}

