// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

public class DeprecateAttributePrefetch extends Processor {

    public DeprecateAttributePrefetch(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (SDField field : search.allConcreteFields()) {
            for (Attribute a : field.getAttributes().values()) {
                if (Boolean.TRUE.equals(a.getPrefetchValue())) {
                    warn(search, field, "Attribute prefetch is deprecated. Use an explicitly defined document summary with all desired fields defined as attribute.");
                }
                if (Boolean.FALSE.equals(a.getPrefetchValue())) {
                    warn(search, field, "Attribute prefetch is deprecated. no-prefetch can be removed.");
                }
            }
        }
    }

}
