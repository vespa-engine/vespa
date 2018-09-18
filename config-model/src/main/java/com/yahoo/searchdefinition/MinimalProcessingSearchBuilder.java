package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.processing.MinimalProcessing;
import com.yahoo.vespa.model.container.search.QueryProfiles;

public class MinimalProcessingSearchBuilder extends SearchBuilder {
    public MinimalProcessingSearchBuilder() {
        super();
    }

    @Override
    protected void process(Search search, DeployLogger deployLogger, QueryProfiles queryProfiles, boolean validate) {
        new MinimalProcessing().process(search, deployLogger, getRankProfileRegistry(), queryProfiles, validate);
    }
}
