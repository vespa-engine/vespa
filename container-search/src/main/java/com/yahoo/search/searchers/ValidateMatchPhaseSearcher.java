// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.config.search.AttributesConfig;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates that the attribute given as match-phase override is actually a valid numeric attribute
 * with fast-search enabled.
 *
 * @author baldersheim
 */
public class ValidateMatchPhaseSearcher extends Searcher {

    private Set<String> validMatchPhaseAttributes = new HashSet<>();
    private Set<String> validDiversityAttributes = new HashSet<>();

    public ValidateMatchPhaseSearcher(AttributesConfig attributesConfig) {
        for (AttributesConfig.Attribute a : attributesConfig.attribute()) {
            if (a.fastsearch() &&
                (a.collectiontype() == AttributesConfig.Attribute.Collectiontype.SINGLE) && isNumeric(a.datatype())) {
                validMatchPhaseAttributes.add(a.name());
            }
        }
        for (AttributesConfig.Attribute a : attributesConfig.attribute()) {
            if ((a.collectiontype() == AttributesConfig.Attribute.Collectiontype.SINGLE) &&
                ((a.datatype() == AttributesConfig.Attribute.Datatype.STRING) || isNumeric(a.datatype()))) {
                validDiversityAttributes.add(a.name());
            }
        }
    }

    private boolean isNumeric(AttributesConfig.Attribute.Datatype.Enum dt) {
        return  dt == AttributesConfig.Attribute.Datatype.DOUBLE ||
                dt == AttributesConfig.Attribute.Datatype.FLOAT ||
                dt == AttributesConfig.Attribute.Datatype.INT8 ||
                dt == AttributesConfig.Attribute.Datatype.INT16 ||
                dt == AttributesConfig.Attribute.Datatype.INT32 ||
                dt == AttributesConfig.Attribute.Datatype.INT64;
    }

    @Override
    public Result search(Query query, Execution execution) {
        ErrorMessage e = validate(query);
        return (e != null)
                ? new Result(query, e)
                : execution.search(query);
    }

    private ErrorMessage validate(Query query) {
        String attribute = query.getRanking().getMatchPhase().getAttribute();
        if ( attribute != null && ! validMatchPhaseAttributes.contains(attribute) ) {
            return ErrorMessage.createInvalidQueryParameter("The attribute '" + attribute + "' is not available for match-phase. " +
                                                            "It must be a single value numeric attribute with fast-search.");
        }
        attribute = query.getRanking().getMatchPhase().getDiversity().getAttribute();
        if (attribute != null && ! validDiversityAttributes.contains(attribute)) {
            return ErrorMessage.createInvalidQueryParameter("The attribute '" + attribute + "' is not available for match-phase diversification. " +
                    "It must be a single value numeric or string attribute.");
        }
        return null;
    }

}
