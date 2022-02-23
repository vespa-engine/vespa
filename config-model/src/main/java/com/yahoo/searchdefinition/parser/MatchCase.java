package com.yahoo.searchdefinition.parser;

public enum MatchCase {
    CASED("cased"),
    UNCASED("uncased");
    private String name;
    MatchCase(String name) { this.name = name; }
    public String getName() { return name;}
}

