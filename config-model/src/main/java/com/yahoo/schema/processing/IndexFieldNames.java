// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Because of the way the parser works (allowing any token as identifier),
 * it is not practical to limit the syntax of field names there, do it here.
 * Important to disallow dash, has semantic in indexing language.
 *
 * @author Vegard Havdal
 */
public class IndexFieldNames extends Processor {

    private static final String FIELD_NAME_REGEXP = "[a-zA-Z]\\w*";

    /**
     * Names beginning with this are reserved for names given a meaning by the query syntax, such as "_",
     * the name of the value of an element itself in a sameElement query. Top level fields cannot begin with
     * it by {@link #FIELD_NAME_REGEXP}, which requires a name to begin with a letter.
     */
    private static final String RESERVED_NAME_PREFIX = "_";

    public IndexFieldNames(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        for (SDField field : schema.allConcreteFields()) {
            if ( ! field.getName().matches(FIELD_NAME_REGEXP) && !field.isInternalField()) {
                fail(schema, field, " Not a legal field name. Legal expression: " + FIELD_NAME_REGEXP);
            }
            validateStructFieldNames(field);
        }
    }

    /** Validates the names of the struct fields of the given field, recursively. */
    private void validateStructFieldNames(SDField field) {
        for (SDField structField : field.getStructFields()) {
            if (leafNameOf(structField).startsWith(RESERVED_NAME_PREFIX) && ! structField.isInternalField()) {
                fail(schema, structField, " Not a legal field name: starting with '" + RESERVED_NAME_PREFIX + "' is reserved.");
            }
            validateStructFieldNames(structField);
        }
    }

    /** Returns the name of the given struct field within its struct, as struct fields are named by their path. */
    private static String leafNameOf(SDField structField) {
        String name = structField.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

}
