// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.document.NumericDataType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Fields that derive to attribute(s) and no indices should use the WORD indexing form,
 * in a feeble attempt to match the most peoples expectations as closely as possible.
 *
 * @author Vegard Havdal
 */
public class AttributesImplicitWord extends Processor {

    public AttributesImplicitWord(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
            if (fieldImplicitlyWordMatch(field)) {
                field.getMatching().setType(Matching.Type.WORD);
            }
        }
    }

    private boolean fieldImplicitlyWordMatch(SDField field) {
        // numeric types should not trigger exact-match query parsing
        DataType dt = field.getDataType().getPrimitiveType();
        if (dt != null && dt instanceof NumericDataType) {
            return false;
        }
        return (field.getIndexToCount() == 0
                && field.getAttributes().size() > 0
                && field.getIndices().isEmpty()
                && !field.getMatching().isTypeUserSet());
    }

}
