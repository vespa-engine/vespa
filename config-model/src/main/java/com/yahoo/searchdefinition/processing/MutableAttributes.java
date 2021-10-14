// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

public class MutableAttributes extends Processor {

    public MutableAttributes(Search search, DeployLogger deployLogger,
                             RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles)
    {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : search.allConcreteFields()) {
            if ( ! field.isExtraField() && field.getAttributes().containsKey(field.getName())) {
                if (field.getAttributes().get(field.getName()).isMutable()) {
                    throw new IllegalArgumentException("Field '" + field.getName() + "' in '" + search.getDocument().getName() +
                                                       "' can not be marked mutable as it is inside the document clause.");
                }
            }
        }
    }
}
