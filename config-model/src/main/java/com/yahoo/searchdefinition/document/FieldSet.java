// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Searchable collection of fields.
 * 
 * @author balder
 */
public class FieldSet {
    
    private final String name;
    private final Set<String> queryCommands = new LinkedHashSet<>();
    private final Set<String> fieldNames = new TreeSet<>();
    private final Set<SDField> fields = new TreeSet<>();
    private Matching matching = null;

    public FieldSet(String name) { this.name = name; }
    public String getName() { return name; }
    public FieldSet addFieldName(String field) { fieldNames.add(field); return this; }
    public Set<String> getFieldNames() { return fieldNames; }
    public Set<SDField> fields() { return fields; }

    public Set<String> queryCommands() {
        return queryCommands;
    }

    public void setMatching(Matching matching) {
        this.matching = matching;
    }

    public Matching getMatching() {
        return matching;
    }

    @Override
    public String toString() { return "fieldset '" + name + "'"; }
    
}
