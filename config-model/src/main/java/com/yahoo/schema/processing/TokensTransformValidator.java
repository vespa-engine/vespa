// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/*
 * Check that summary fields with summary transform 'tokens' have a source field with a data type that is one of
 * string, array<string> or weightedset<string>.
 */
public class TokensTransformValidator extends Processor {
    public TokensTransformValidator(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (!validate || documentsOnly) {
            return;
        }
        for (var summary : schema.getSummaries().values()) {
            for (var summaryField : summary.getSummaryFields().values()) {
                if (summaryField.getTransform().isTokens()) {
                    var source = summaryField.getSingleSource();
                    if (source != null) {
                        var field = schema.getField(source);
                        if (field != null) {
                            var type = field.getDataType();
                            var innerType = type.getPrimitiveType();
                            if (innerType != DataType.STRING) {
                                throw new IllegalArgumentException("For schema '" + schema.getName() +
                                        "', document-summary '" + summary.getName() +
                                        "', summary field '" + summaryField.getName() +
                                        "', source field '" + field.getName() +
                                        "', source field type '" + type.getName() +
                                        "': transform '" + SummaryTransform.TOKENS.getName() +
                                        "' is only allowed for fields of type" +
                                        " string, array<string> or weightedset<string>");
                            }
                        }
                    }
                }
            }
        }
    }
}
