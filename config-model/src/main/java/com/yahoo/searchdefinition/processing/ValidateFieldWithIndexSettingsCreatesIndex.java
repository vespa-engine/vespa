// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.Ranking;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Check that fields with index settings actually creates an index or attribute
 *
 * @author bratseth
 */
public class ValidateFieldWithIndexSettingsCreatesIndex extends Processor {

    public ValidateFieldWithIndexSettingsCreatesIndex(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        Matching defaultMatching = new Matching();
        Ranking defaultRanking = new Ranking();
        for (SDField field : search.allConcreteFields()) {
            if (field.doesIndexing()) continue;
            if (field.doesAttributing()) continue;

            if ( ! field.getRanking().equals(defaultRanking))
                fail(search, field,
                     "Fields which are not creating an index or attribute can not contain rank settings.");
            if ( ! field.getMatching().equals(defaultMatching))
                fail(search, field,
                     "Fields which are not creating an index or attribute can not contain match settings.");
        }
    }

}
