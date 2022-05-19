// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.HashMap;
import java.util.Map;

public class ValidateFieldTypesDocumentsOnly extends ValidateFieldTypes {
    public ValidateFieldTypesDocumentsOnly(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        String searchName = schema.getName();
        Map<String, DataType> seenFields = new HashMap<>();
        verifySearchAndDocFields(searchName, seenFields);
    }
}
