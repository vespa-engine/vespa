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
 * This class holds the extracted information after parsing a summary
 * field declaration, either from "field" inside "document-summary" or
 * "summary" inside "field".  Using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedSummaryField {

    private final String name;
    private final ParsedType type;
    private boolean isDyn = false;
    private boolean isMEO = false;
    private boolean isFull = false;
    private boolean isBold = false;
    private final List<String> sources = new ArrayList<>();
    private final List<String> destinations = new ArrayList<>();

    ParsedSummaryField(String name) {
        this.name = name;
        this.type = null;
    }

    ParsedSummaryField(String name, ParsedType type) {
        this.name = name;
        this.type = type;
    }

    String name() { return name; }
    ParsedType type() { return type; }
    List<String> getDestinations() { return ImmutableList.copyOf(destinations); }
    List<String> getSources() { return ImmutableList.copyOf(sources); }
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
}
