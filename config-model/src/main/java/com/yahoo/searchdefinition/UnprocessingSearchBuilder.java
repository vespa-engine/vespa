// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.io.IOException;

/**
 * A SearchBuilder that does not run the processing chain for searches
 */
public class UnprocessingSearchBuilder extends SearchBuilder {

    public UnprocessingSearchBuilder(ApplicationPackage app,
                                     RankProfileRegistry rankProfileRegistry,
                                     QueryProfileRegistry queryProfileRegistry) {
        super(app, rankProfileRegistry, queryProfileRegistry);
    }

    public UnprocessingSearchBuilder() {
        super();
    }

    public UnprocessingSearchBuilder(RankProfileRegistry rankProfileRegistry,
                                     QueryProfileRegistry queryProfileRegistry) {
        super(rankProfileRegistry, queryProfileRegistry);
    }

    @Override
    public void process(Search search, DeployLogger deployLogger, QueryProfiles queryProfiles) {
        // empty
    }

    public static Search buildUnprocessedFromFile(String fileName) throws IOException, ParseException {
        SearchBuilder builder = new UnprocessingSearchBuilder();
        builder.importFile(fileName);
        builder.build();
        return builder.getSearch();
    }
}
