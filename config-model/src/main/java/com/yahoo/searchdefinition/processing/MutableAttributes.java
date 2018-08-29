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
    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
            if (!field.isExtraField() && field.getAttributes().containsKey(field.getName())) {
                if (field.getAttributes().get(field.getName()).isMutable()) {
                    throw new IllegalArgumentException("Field " + field + " in '" + search.getDocument().getName() +
                            "' can not be marked mutable as it inside the document.");
                }
            }
        }
    }
}
