// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.searchdefinition.document.Stemming;

/**
 * This class holds the extracted information after parsing a "field"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
class ParsedField {

    private final String name;
    private ParsedType type;

    ParsedField(String name, ParsedType type) {
        this.name = name;
        this.type = type;
    }

    String name() { return this.name; }
    ParsedType getType() { return this.type; }

    void addAlias(String from, String to) {}
    void addIndex(ParsedIndex index) {}
    void addRankType(String index, String rankType) {}
    void dictionary(DictionaryOption option) {}
    void setBolding(boolean value) {}
    void setFilter(boolean value) {}
    void setId(int id) {}
    void setIndexingRewrite(boolean value) {}
    void setLiteral(boolean value) {}
    void setNormal(boolean value) {}
    void setNormalizing(String value) {}
    void setSorting(ParsedSorting sorting) {}
    void setStemming(Stemming stemming) {}
    void setWeight(int weight) {}
    void addAttribute(ParsedAttribute attr) {}
    void addIndexingOperation(Object indx) {}
    void addMatchSettings(ParsedMatchSettings settings) {}
    void addQueryCommand(String queryCommand) {}
    void addStructField(ParsedField structField) {}
    void addSummaryField(ParsedSummaryField summaryField) {}
}
