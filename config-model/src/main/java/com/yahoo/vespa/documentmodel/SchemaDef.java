// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author baldersheim
 */
public class SchemaDef {

    private final static Logger log = Logger.getLogger(SchemaDef.class.getName());

    /** Name of the schema. */
    private final String name;

    /** These are the real backing document types. */
    private final DocumentTypeManager sources = new DocumentTypeManager();

    /** All fields in this. */
    private final Map<String, SearchField> fields = new HashMap<>();

    /**
     * All index names. This includes any additional index names created by index blocks inside fields.
     * TODO: That feature is deprecated and should be removed on Vespa 9.
     */
    private final Set<String> indexNames = new HashSet<>();

    /** Map of all aliases <alias, realname> */
    private final Map<String, String> aliases = new HashMap<>();

    /** Creates a SearchDef with the given name. */
    public SchemaDef(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public Map<String, SearchField> getFields() { return fields; }
    public Set<String> getIndexNames() { return indexNames; }

    /**
     * Adds a document that can be mapped to this schema.
     *
     * @param source a document that can be mapped to this schema.
     * @return Itself for chaining.
     */
    public SchemaDef add(DocumentType source) {
        sources.registerDocumentType(source);
        return this;
    }

    private void noShadowing(String name) {
        noFieldShadowing(name);
        noViewShadowing(name);
    }

    private void noFieldShadowing(String name) {
        if (fields.containsKey(name)) {
            throw new IllegalArgumentException("Schema '" + getName() + "' already contains the fields '" + fields +
                                               "'. You are trying to add '" + name + "'. Shadowing is not supported");
        }
    }

    private void noViewShadowing(String name) {
        if (indexNames.contains(name)) {
            throw new IllegalArgumentException("Schema '" + getName() + "' already contains an index with name '" +
                                               name + "'. Shadowing is not supported.");
        }
    }

    /**
     * Adds a search field to the schema.
     *
     * @param field the field to add
     * @return this, for chaining
     */
    public SchemaDef add(SearchField field) {
        try {
            noFieldShadowing(field.getName());
            fields.put(field.getName(), field);
        } catch (IllegalArgumentException e) {
            if (indexNames.contains(field.getName())) {
                 throw e;
            }
        }
        return this;
    }

    public SchemaDef addAlias(String alias, String aliased) {
        noShadowing(alias);
        if (!fields.containsKey(aliased) && !indexNames.contains(aliased)) {
            if (aliased.contains(".")) {
                // TODO Here we should nest ourself down to something that really exists.
                log.warning("Aliased item '" + aliased + "' not verifiable. Allowing it to be aliased to '" +
                            alias + " for now. Validation will come when URL/Position is structified.");
            } else {
                throw new IllegalArgumentException("Schema '" + getName() + "' has nothing named '" +
                                                   aliased + "'to alias to '" + alias + "'.");
            }
        }
        String oldAliased = aliases.get(alias);
        if ((oldAliased != null)) {
            if (oldAliased.equals(aliased)) {
                throw new IllegalArgumentException("Schema '" + getName() + "' already has the alias '" + alias +
                                                   "' to '" + aliased + ". Why do you want to add it again.");

            } else {
                throw new IllegalArgumentException("Schema '" + getName() + "' already has the alias '" + alias +
                                                   "' to '" + oldAliased + ". Cannot change it to alias '" + aliased + "'.");
            }
        } else {
            aliases.put(alias, aliased);
        }
        return this;
    }

    public SchemaDef addIndexName(String indexName) {
        noViewShadowing(indexName);
        indexNames.add(indexName);
        return this;
    }

}
