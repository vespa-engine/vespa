// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class ParsedSummaryField extends ParsedBlock {

    private ParsedType type;
    private boolean isDyn = false;
    private boolean isMEO = false;
    private boolean isFull = false;
    private boolean isBold = false;
    private boolean isTokens = false;
    private boolean hasExplicitType = false;
    private final List<String> sources = new ArrayList<>();
    private final List<String> destinations = new ArrayList<>();

    public ParsedSummaryField(String name) {
        this(name, null);
    }

    public ParsedSummaryField(String name, ParsedType type) {
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
    boolean getTokens() { return isTokens; }
    boolean getHasExplicitType() { return hasExplicitType; }

    public void addDestination(String dst) { destinations.add(dst); }
    public void addSource(String src) { sources.add(src); }
    public void setBold(boolean value) { this.isBold = value; }
    public void setDynamic() { this.isDyn = true; }
    public void setFull() { this.isFull = true; }
    public void setMatchedElementsOnly() { this.isMEO = true; }
    public void setTokens() { this.isTokens = true; }
    public void setHasExplicitType() { this.hasExplicitType = true; }
    void setType(ParsedType value) {
        verifyThat(type == null, "Cannot change type from ", type, "to", value);
        this.type = value;
    }
}
