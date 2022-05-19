// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.yahoo.schema.document.FieldSet;

/**
 * The field sets owned by a {@link Schema}
 * Both built in and user defined.
 *
 * @author vegardh
 */
public class FieldSets {

    private final Optional<Schema> owner;
    private final Map<String, FieldSet> userFieldSets;
    private final Map<String, FieldSet> builtInFieldSets;

    public FieldSets(Optional<Schema> owner) {
        this.owner = owner;
        userFieldSets = new LinkedHashMap<>();
        builtInFieldSets = new LinkedHashMap<>();
    }

    /**
     * Adds an entry to user field sets, creating entries as needed
     *
     * @param setName name of a field set
     * @param field field to add to field set
     */
    public void addUserFieldSetItem(String setName, String field) {
        if (userFieldSets.get(setName) == null) {
            // First entry in this set
            userFieldSets.put(setName, new FieldSet(setName));
        }
        userFieldSets.get(setName).addFieldName(field);
    }

    /**
     * Adds an entry to built in field sets, creating entries as needed
     *
     * @param setName name of a field set
     * @param field field to add to field set
     */
    public void addBuiltInFieldSetItem(String setName, String field) {
        if (builtInFieldSets.get(setName) == null) {
            // First entry in this set
            builtInFieldSets.put(setName, new FieldSet(setName));
        }
        builtInFieldSets.get(setName).addFieldName(field);
    }

    /** Returns the built in field sets, unmodifiable */
    public Map<String, FieldSet> builtInFieldSets() {
        if (owner.isEmpty() || owner.get().inherited().isEmpty()) return Collections.unmodifiableMap(builtInFieldSets);
        if (builtInFieldSets.isEmpty()) return owner.get().inherited().get().fieldSets().builtInFieldSets();

        var fieldSets = new LinkedHashMap<>(owner.get().inherited().get().fieldSets().builtInFieldSets());
        fieldSets.putAll(builtInFieldSets);
        return Collections.unmodifiableMap(fieldSets);
    }
    
    /** Returns the user defined field sets, unmodifiable */
    public Map<String, FieldSet> userFieldSets() {
        if (owner.isEmpty() || owner.get().inherited().isEmpty()) return Collections.unmodifiableMap(userFieldSets);
        if (userFieldSets.isEmpty()) return owner.get().inherited().get().fieldSets().userFieldSets();

        var fieldSets = new LinkedHashMap<>(owner.get().inherited().get().fieldSets().userFieldSets());
        fieldSets.putAll(userFieldSets);
        return Collections.unmodifiableMap(fieldSets);
    }
    
}
