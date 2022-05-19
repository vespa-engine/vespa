// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

public class StringSettingsOnNonStringFields extends Processor {

    public StringSettingsOnNonStringFields(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        for (SDField field : schema.allConcreteFields()) {
            if ( ! doCheck(field)) continue;
            if (field.getMatching().isTypeUserSet()) {
                warn(schema, field, "Matching type " + field.getMatching().getType() +
                                    " is only allowed for string fields.");
            }
            if (field.getRanking().isLiteral()) {
                warn(schema, field, "Rank type literal only applies to string fields");
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
