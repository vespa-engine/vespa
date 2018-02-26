// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

public class StringSettingsOnNonStringFields extends Processor {

    public StringSettingsOnNonStringFields(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (SDField field : search.allConcreteFields()) {
            if ( ! doCheck(field)) continue;
            if (field.getMatching().isTypeUserSet()) {
                warn(search, field, "Matching type " + field.getMatching().getType() +
                                    " is only allowed for string fields.");
            }
            if (field.getRanking().isLiteral()) {
                warn(search, field, "Rank type literal only applies to string fields");
            }
        }
    }

    private boolean doCheck(SDField field) {
        if (field.getDataType() instanceof NumericDataType) return true;
        if (field.getDataType() instanceof CollectionDataType) {
            if (((CollectionDataType)field.getDataType()).getNestedType() instanceof NumericDataType) {
                return true;
            }
        }
        return false;
    }
}
