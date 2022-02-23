// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the extracted information after parsing a summary
 * field declaration, either from "field" inside "document-summary" or
 * "summary" inside "field".  Using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedSummaryField {

    public final String name;
    public final Object type;

    ParsedSummaryField(String name) {
        this.name = name;
        this.type = null;
    }
    ParsedSummaryField(String name, Object type) {
        this.name = name;
        this.type = type;
    }

    String getName() { return name; }
    Object getType() { return type; }

    boolean isDyn = false;
    boolean isMEO = false;
    boolean isFull = false;
    boolean isBold = false;
    final List<String> sources = new ArrayList<>();
    final List<String> destinations = new ArrayList<>();

    void addDestination(String dst) { destinations.add(dst); }
    void addSource(String src) { sources.add(src); }
    void setBold(boolean value) { this.isBold = value; }
    void setDynamic() { this.isDyn = true; }
    void setFull() { this.isFull = true; }
    void setMatchedElementsOnly() { this.isMEO = true; }
}
