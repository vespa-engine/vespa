// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the extracted information after parsing a summary
 * field declaration, either from "field" inside "document-summary" or
 * "summary" inside "field".  Using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedSummaryField extends ParsedBlock {

    private ParsedType type;
    private boolean isDyn = false;
    private boolean isMEO = false;
    private boolean isFull = false;
    private boolean isBold = false;
    private final List<String> sources = new ArrayList<>();
    private final List<String> destinations = new ArrayList<>();

    ParsedSummaryField(String name) {
        this(name, null);
    }

    ParsedSummaryField(String name, ParsedType type) {
        super(name, "summary field");
        this.type = type;
    }

    ParsedType getType() { return type; }
    List<String> getDestinations() { return List.copyOf(destinations); }
    List<String> getSources() { return List.copyOf(sources); }
    boolean getBolded() { return isBold; }
    boolean getDynamic() { return isDyn; }
    boolean getFull() { return isFull; }
    boolean getMatchedElementsOnly() { return isMEO; }

    void addDestination(String dst) { destinations.add(dst); }
    void addSource(String src) { sources.add(src); }
    void setBold(boolean value) { this.isBold = value; }
    void setDynamic() { this.isDyn = true; }
    void setFull() { this.isFull = true; }
    void setMatchedElementsOnly() { this.isMEO = true; }
    void setType(ParsedType value) {
        verifyThat(type == null, "Cannot change type from ", type, "to", value);
        this.type = value;
    }
}
