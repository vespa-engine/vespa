// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.yahoo.searchdefinition.document.FieldSet;

/**
 * The field sets owned by a {@link Search}
 * Both built in and user defined.
 *
 * @author vegardh
 */
public class FieldSets {

    private final Map<String, FieldSet> userFieldSets = new LinkedHashMap<>();
    private final Map<String, FieldSet> builtInFieldSets = new LinkedHashMap<>();

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
        return Collections.unmodifiableMap(builtInFieldSets);
    }
    
    /** Returns the user defined field sets, unmodifiable */
    public Map<String, FieldSet> userFieldSets() {
        return Collections.unmodifiableMap(userFieldSets);
    }
    
}
