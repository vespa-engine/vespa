// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

public enum MatchType {
    TEXT("text"),
    WORD("word"),
    EXACT("exact"),
    GRAM("gram");

    private String name;
    MatchType(String name) { this.name = name; }

    public String getName() { return name; }
}
