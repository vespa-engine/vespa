// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.RankType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * All rank: filter fields should have rank type empty.
 *
 * @author bratseth
 */
public class SetRankTypeEmptyOnFilters extends Processor {

    public SetRankTypeEmptyOnFilters(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            if (field.getRanking().isFilter()) {
                field.setRankType(RankType.EMPTY);
            }
        }
    }
}
