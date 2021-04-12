package com.yahoo.searchdefinition.document;

public enum Case {
    CASED("cased"),
    UNCASED("uncased");
    private String name;
    Case(String name) { this.name = name; }
    public String getName() { return name;}
}
