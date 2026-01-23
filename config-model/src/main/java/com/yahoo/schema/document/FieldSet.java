// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Comparator.comparing;

/**
 * A set of fields that can be searched as one.
 * 
 * @author baldersheim
 */
public class FieldSet {
    
    private final String name;
    private final Set<String> queryCommands = new LinkedHashSet<>();
    private final Set<String> fieldNames = new TreeSet<>();
    private final Set<ImmutableSDField> fields = new TreeSet<>(comparing(ImmutableSDField::asField));
    private Matching matching = null;
    private String linguisticsProfile = null;

    public FieldSet(String name) { this.name = name; }
    public String getName() { return name; }
    public FieldSet addFieldName(String field) { fieldNames.add(field); return this; }
    public Set<String> getFieldNames() { return fieldNames; }

    /** Returns the immutable set of fields of this. The fields are not assigned until the derive step. */
    public Set<ImmutableSDField> fields() { return fields; }

    public Set<String> queryCommands() { return queryCommands; }

    public void setMatching(Matching matching) {
        this.matching = matching;
    }

    public Matching getMatching() {
        return matching;
    }

    public void setLinguisticsProfile(String profile) {
        this.linguisticsProfile = profile;
    }

    /** Returns the (search side) linguistics profile to use in this, or null if none/not yet assigned. */
    public String getLinguisticsProfile() {
        return linguisticsProfile;
    }

    @Override
    public String toString() { return "fieldset '" + name + "'"; }
    
}
