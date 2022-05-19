// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

public class MutableAttributes extends Processor {

    public MutableAttributes(Schema schema, DeployLogger deployLogger,
                             RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles)
    {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            if ( ! field.isExtraField() && field.getAttributes().containsKey(field.getName())) {
                if (field.getAttributes().get(field.getName()).isMutable()) {
                    throw new IllegalArgumentException("Field '" + field.getName() + "' in '" + schema.getDocument().getName() +
                                                       "' can not be marked mutable as it is inside the document clause.");
                }
            }
        }
    }
}
