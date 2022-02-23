package com.yahoo.searchdefinition.parser;

public enum MatchType {
    TEXT("text"),
    WORD("word"),
    EXACT("exact"),
    GRAM("gram");
    private String name;
    MatchType(String name) { this.name = name; }
    public String getName() { return name; }
}
