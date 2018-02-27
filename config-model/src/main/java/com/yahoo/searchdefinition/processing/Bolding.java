// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Checks that bolding or dynamic summary is turned on only for text fields. Throws exception if it is turned on for any
 * other fields (otherwise will cause indexing failure)
 *
 * @author hmusum
 */
public class Bolding extends Processor {

    public Bolding(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;
        for (SDField field : search.allConcreteFields()) {
            for (SummaryField summary : field.getSummaryFields()) {
                if (summary.getTransform().isBolded() &&
                    !((summary.getDataType() == DataType.STRING) || (summary.getDataType() == DataType.URI)))
                {
                    throw new IllegalArgumentException("'bolding: on' for non-text field " +
                                                       "'" + field.getName() + "'" +
                                                       " (" + summary.getDataType() + ")" +
                                                       " is not allowed");
                } else if (summary.getTransform().isDynamic() &&
                           !((summary.getDataType() == DataType.STRING) || (summary.getDataType() == DataType.URI)))
                {
                    throw new IllegalArgumentException("'summary: dynamic' for non-text field " +
                                                       "'" + field.getName() + "'" +
                                                       " (" + summary.getDataType() + ")" +
                                                       " is not allowed");
                }
            }
        }
    }
}
