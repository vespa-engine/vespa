// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * A search must have a document definition of the same name inside of it, otherwise crashes may occur as late as
 * during feeding
 *
 * @author Vegard Havdal
 */
public class SearchMustHaveDocument extends Processor {

    public SearchMustHaveDocument(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        if (search.getDocument() == null)
            throw new IllegalArgumentException("For search '" + search.getName() +
                                               "': A search specification must have an equally named document inside of it.");
    }

}
