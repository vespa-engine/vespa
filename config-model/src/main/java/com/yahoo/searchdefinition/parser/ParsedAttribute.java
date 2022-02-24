// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

/**
 * This class holds the extracted information after parsing a
 * "attribute" block, using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedAttribute extends ParsedBlock {

    ParsedAttribute(String name) {
        super(name, "attribute");
    }

    void addAlias(String from, String to) {}
    void setDistanceMetric(String metric) {}
    void setEnableBitVectors(boolean value) {}
    void setEnableOnlyBitVector(boolean value) {}
    void setFastAccess(boolean value) {}
    void setFastSearch(boolean value) {}
    void setHuge(boolean value) {}
    void setMutable(boolean value) {}
    void setPaged(boolean value) {}
    void setSorting(ParsedSorting sorting) {}
}
