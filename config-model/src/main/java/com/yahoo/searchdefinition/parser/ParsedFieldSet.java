package com.yahoo.searchdefinition.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the extracted information after parsing a "fieldset"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
class ParsedFieldSet extends ParsedBlock {

    private final List<String> fields = new ArrayList<>();
    private final List<String> queryCommands = new ArrayList<>();
    private final ParsedMatchSettings matchInfo = new ParsedMatchSettings();

    ParsedFieldSet(String name) {
        super(name, "fieldset");
    }

    ParsedMatchSettings matchSettings() { return this.matchInfo; }
    List<String> getQueryCommands() { return List.copyOf(queryCommands); }
    List<String> getFieldNames() { return List.copyOf(fields); }

    void addField(String field) { fields.add(field); }
    void addQueryCommand(String command) { queryCommands.add(command); }
}
