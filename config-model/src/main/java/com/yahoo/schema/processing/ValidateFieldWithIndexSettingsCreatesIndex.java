// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.Ranking;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Check that fields with index settings actually creates an index or attribute
 *
 * @author bratseth
 */
public class ValidateFieldWithIndexSettingsCreatesIndex extends Processor {

    public ValidateFieldWithIndexSettingsCreatesIndex(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        Matching defaultMatching = new Matching();
        Ranking defaultRanking = new Ranking();
        for (SDField field : schema.allConcreteFields()) {
            if (field.doesIndexing()) continue;
            if (field.doesAttributing()) continue;

            if ( ! field.getRanking().equals(defaultRanking))
                fail(schema, field,
                     "Fields which are not creating an index or attribute can not contain rank settings.");
            if ( ! field.getMatching().equals(defaultMatching))
                fail(schema, field,
                     "Fields which are not creating an index or attribute can not contain match settings.");
        }
    }

}
