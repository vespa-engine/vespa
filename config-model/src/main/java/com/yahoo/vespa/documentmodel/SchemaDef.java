// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;

import java.util.HashMap;
import java.util.Map;
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

    /** Map of all search fields. */
    private final Map<String, SearchField> fields = new HashMap<>();

    /** Map of all views that can be searched. */
    private final Map<String, FieldView> views = new HashMap<>();

    /// Map of all aliases <alias, realname>
    private final Map<String, String> aliases = new HashMap<>();

    /** Creates a SearchDef with the given name. */
    public SchemaDef(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public Map<String, SearchField> getFields() { return fields; }
    public Map<String, FieldView> getViews() { return views; }

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
        if (views.containsKey(name)) {
            throw new IllegalArgumentException("Searchdef '" + getName() + "' already contains a view with name '" +
                    name + "'. Shadowing is not supported.");
        }
    }

    /**
     * Adds a search field to the schema.
     *
     * @param field the field to add.
     * @return this, for chaining.
     */
    public SchemaDef add(SearchField field) {
        try {
            noFieldShadowing(field.getName());
            fields.put(field.getName(), field);
        } catch (IllegalArgumentException e) {
            if (views.containsKey(field.getName())) {
                 throw e;
            }
        }
        return this;
    }

    public SchemaDef addAlias(String alias, String aliased) {
        noShadowing(alias);
        if (!fields.containsKey(aliased) && !views.containsKey(aliased)) {
            if (aliased.contains(".")) {
                // TODO Here we should nest ourself down to something that really exists.
                log.warning("Aliased item '" + aliased + "' not verifiable. Allowing it to be aliased to '" + alias + " for now. Validation will come when URL/Position is structified.");
            } else {
                throw new IllegalArgumentException("Searchdef '" + getName() + "' has nothing named '" + aliased + "'to alias to '" + alias + "'.");
            }
        }
        String oldAliased = aliases.get(alias);
        if ((oldAliased != null)) {
            if (oldAliased.equals(aliased)) {
                throw new IllegalArgumentException("Searchdef '" + getName() + "' already has the alias '" + alias +
                        "' to '" + aliased + ". Why do you want to add it again.");

            } else {
                throw new IllegalArgumentException("Searchdef '" + getName() + "' already has the alias '" + alias +
                        "' to '" + oldAliased + ". Cannot change it to alias '" + aliased + "'.");
            }
        } else {
            aliases.put(alias, aliased);
        }
        return this;
    }

    public SchemaDef add(FieldView view) {
        noViewShadowing(view.getName());
        if (views.containsKey(view.getName())) {
            views.get(view.getName()).add(view);
        }
        views.put(view.getName(), view);
        return this;
    }

}
