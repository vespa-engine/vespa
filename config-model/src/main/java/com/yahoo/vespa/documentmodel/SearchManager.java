// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import java.util.TreeMap;

/**
 * @author    baldersheim
 */
public class SearchManager {

    /// This is the list of all known search definitions
    private TreeMap<String, SearchDef> defs = new TreeMap<>();

    /**
     * This will add a searchdefinition or throw an IllegalArgumentException if the name is already used
     * @param def The searchdef to add
     * @return itself for chaining purposes.
     */
    public SearchManager add(SearchDef def) {
        if (defs.containsKey(def.getName())) {
            throw new IllegalArgumentException("There already exist a searchdefinition with this content:\n" +
                    defs.get(def.getName()).toString() + "\n No room for : " + def.toString());
        }
        defs.put(def.getName(), def);
        return this;
    }

}
