// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * @author baldersheim
 */
public class IndexTo2FieldSet extends Processor {

    public IndexTo2FieldSet(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process() {
        for (SDField field : search.allConcreteFields()) {
            processField(field);
        }
    }
    private void processField(SDField field) {
        if (field.usesStructOrMap()) {
            for (SDField subField : field.getStructFields()) {
                processField(subField);
            }
        }
    }

}
