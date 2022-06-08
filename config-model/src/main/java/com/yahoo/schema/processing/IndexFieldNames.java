// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Because of the way the parser works (allowing any token as identifier),
 * it is not practical to limit the syntax of field names there, do it here.
 * Important to disallow dash, has semantic in IL.
 *
 * @author Vehard Havdal
 */
public class IndexFieldNames extends Processor {

    private static final String FIELD_NAME_REGEXP = "[a-zA-Z]\\w*";

    public IndexFieldNames(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        for (SDField field : schema.allConcreteFields()) {
            if ( ! field.getName().matches(FIELD_NAME_REGEXP) &&  ! legalDottedPositionField(field)) {
                fail(schema, field, " Not a legal field name. Legal expression: " + FIELD_NAME_REGEXP);
            }
        }
    }

    /**
     * In {@link CreatePositionZCurve} we add some .position and .distance fields for pos fields. Make an exception for those for now.
     * TODO Vespa 9: delete this method.
     *
     * @param field an {@link com.yahoo.schema.document.SDField}
     * @return true if allowed
     */
    private boolean legalDottedPositionField(SDField field) {
        return field.getName().endsWith(".position") || field.getName().endsWith(".distance");
    }

}
