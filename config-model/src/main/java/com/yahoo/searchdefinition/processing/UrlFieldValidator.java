// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * @author bratseth
 */
public class UrlFieldValidator extends Processor {

    public UrlFieldValidator(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (SDField field : search.allConcreteFields()) {
            if  ( ! field.getDataType().equals(DataType.URI)) continue;

            if (field.doesAttributing())
                throw new IllegalArgumentException("Error in " + field + " in " + search + ": " +
                                                   "uri type fields cannot be attributes");
        }

    }

}
