package com.yahoo.searchdefinition.parser;

public enum MatchAlgorithm {
    NORMAL("normal"),
    PREFIX("prefix"),
    SUBSTRING("substring"),
    SUFFIX("suffix");
    private String name;
    MatchAlgorithm(String name) { this.name = name; }
    public String getName() { return name; }
}
