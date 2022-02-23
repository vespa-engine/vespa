// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

/**
 * This class holds the extracted information after parsing a
 * "function" block in a rank-profile, using simple data structures as
 * far as possible.  Do not put advanced logic here!
 **/
class ParsedRankFunction {

    private final String name;

    ParsedRankFunction(String name) {
        this.name = name;
    }

    void addParameter(String param) {}
    void setInline(boolean inline) {}
    void setExpression(String expr) {}
}
