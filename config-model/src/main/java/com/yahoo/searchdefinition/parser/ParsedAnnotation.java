package com.yahoo.searchdefinition.parser;

/**
 * This class holds the extracted information after parsing a
 * "annotation" block, using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedAnnotation {

    private final String name;

    ParsedAnnotation(String name) {
        this.name = name;
    }

    public String name() { return name; }

    void setStruct(ParsedStruct struct) {}
    void inherit(String other) {}
}
