package com.yahoo.searchdefinition.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the extracted information after parsing a "fieldset"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
class ParsedFieldSet {

    public final String name;
    final List<String> fields = new ArrayList<>();

    ParsedFieldSet(String name) {
        this.name = name;
    }

    void addField(String field) { fields.add(field); }
    void addQueryCommand(String queryCommand) {}
    void addMatchSettings(ParsedMatchSettings matchInfo) {}
}
