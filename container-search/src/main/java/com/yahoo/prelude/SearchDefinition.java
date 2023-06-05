// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import java.util.HashMap;
import java.util.Map;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * An object for storing information about search definitions in the centralized
 * directory services.
 *
 * @author Steinar Knutsen
 */
// TODO: Make freezable!
public class SearchDefinition {

    private final String name;

    /** A map of all indices in this search definition, indexed by name */
    private final Map<String, Index> indices = new HashMap<>();

    /* A map of all indices in this search definition, indexed by lower cased name. */
    private final Map<String, Index> lowerCase = new HashMap<>();

    private String defaultPosition;

    public SearchDefinition(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public String getDefaultPosition() {
        return defaultPosition;
    }

    public void addIndex(Index index) {
        indices.put(index.getName(), index);
        lowerCase.put(toLowerCase(index.getName()), index);
        if (index.isDefaultPosition()) {
            defaultPosition = index.getName();
        }
    }

    public void addAlias(String alias, String indexName) {
        Index old = indices.get(alias);
        if (old != null) {
            if (old.getName().equals(indexName)) return;
            throw new IllegalArgumentException("Tried adding the alias '" + alias + "' for the index name '" +
                                               indexName + "' when the name '" + alias +
                                               "' already maps to '" + old.getName() + "'");
        }
        Index index = indices.get(indexName);
        if (index == null)
            throw new IllegalArgumentException("Failed adding alias '" + alias + "' for the index name '" + indexName +
                                               "' as there is no index with that name available.");
        indices.put(alias, index);
        index.addAlias(alias);

        String lca = toLowerCase(alias);
        if (lowerCase.get(lca) == null)
            lowerCase.put(lca, index);
    }

    public Index getIndex(String name) {
        return indices.get(name);
    }

    public Index getIndexByLowerCase(String name) {
        return lowerCase.get(name);
    }

    /** Returns the indices of this as a map */
    public Map<String, Index> indices() {
        return indices;
    }

    public Index getOrCreateIndex(String name) {
        Index idx = getIndex(name);
        if (idx != null) {
            return idx;
        }
        idx = new Index(name);
        addIndex(idx);
        return idx;
    }

    public Index addCommand(String indexName, String commandString) {
        Index index = getOrCreateIndex(indexName);
        index.addCommand(commandString);
        if (index.isDefaultPosition()) {
            defaultPosition = index.getName();
        }
        return index;
    }

}
