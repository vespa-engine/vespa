// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Comparator.comparing;

/**
 * Searchable collection of fields.
 * 
 * @author baldersheim
 */
public class FieldSet {
    
    private final String name;
    private final Set<String> queryCommands = new LinkedHashSet<>();
    private final Set<String> fieldNames = new TreeSet<>();
    private final Set<ImmutableSDField> fields = new TreeSet<>(comparing(ImmutableSDField::asField));
    private Matching matching = null;

    public FieldSet(String name) { this.name = name; }
    public String getName() { return name; }
    public FieldSet addFieldName(String field) { fieldNames.add(field); return this; }
    public Set<String> getFieldNames() { return fieldNames; }
    public Set<ImmutableSDField> fields() { return fields; }
    public Set<String> queryCommands() { return queryCommands; }

    public void setMatching(Matching matching) {
        this.matching = matching;
    }

    public Matching getMatching() {
        return matching;
    }

    @Override
    public String toString() { return "fieldset '" + name + "'"; }
    
}
