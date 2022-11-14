// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

/** Which match algorithm is used by this matching setup */
public enum MatchAlgorithm {

    NORMAL("normal"),
    PREFIX("prefix"),
    SUBSTRING("substring"),
    SUFFIX("suffix");

    private final String name;

    MatchAlgorithm(String name) { this.name = name; }

    public String getName() { return name; }

}
