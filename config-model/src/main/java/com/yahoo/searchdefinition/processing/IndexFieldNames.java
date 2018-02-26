// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
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

    public IndexFieldNames(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (SDField field : search.allConcreteFields()) {
            if ( ! field.getName().matches(FIELD_NAME_REGEXP) &&  ! legalDottedPositionField(field)) {
                fail(search, field, " Not a legal field name. Legal expression: " + FIELD_NAME_REGEXP);
            }
        }
    }

    /**
     * In {@link CreatePositionZCurve} we add some .position and .distance fields for pos fields. Make an exception for those for now. For 6.0, rename
     * to _position and _distance and delete this method.
     *
     * @param field an {@link com.yahoo.searchdefinition.document.SDField}
     * @return true if allowed
     */
    private boolean legalDottedPositionField(SDField field) {
        return field.getName().endsWith(".position") || field.getName().endsWith(".distance");
    }

}
