// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

class ParsedRankFunction {

    private final String name;

    ParsedRankFunction(String name) {
        this.name = name;
    }

    void addParameter(String param) {}
    void setInline(boolean inline) {}
    void setExpression(String expr) {}
}
