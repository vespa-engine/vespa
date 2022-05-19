// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

/**
 * Describes if items should preserve lower/upper case, or shall be uncased
 * which normally mean they are all normalized to lowercase.
 * @author baldersheim
 */
public enum Case {
    CASED("cased"),
    UNCASED("uncased");
    private String name;
    Case(String name) { this.name = name; }
    public String getName() { return name;}
}
