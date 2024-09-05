// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import java.util.TreeMap;

/**
 * @author baldersheim
 */
public class SearchManager {

    /** The list of all known schemas. */
    private final TreeMap<String, SchemaDef> schema = new TreeMap<>();

    /**
     * Adds a schema or throw an IllegalArgumentException if the name is already used
     *
     * @param schema the schema to add
     * @return itself for chaining purposes.
     */
    public SearchManager add(SchemaDef schema) {
        if (this.schema.containsKey(schema.getName())) {
            throw new IllegalArgumentException("There already exist a schema with this content:\n" +
                                               this.schema.get(schema.getName()).toString() + "\n No room for : " + schema);
        }
        this.schema.put(schema.getName(), schema);
        return this;
    }

}
