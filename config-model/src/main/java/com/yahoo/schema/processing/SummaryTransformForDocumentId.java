// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SummaryClass;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Adds the corresponding summary transform for all "documentid" summary fields.
 *
 * @author geirst
 */
public class SummaryTransformForDocumentId extends Processor {

    public SummaryTransformForDocumentId(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (var summary : schema.getSummaries().values()) {
            for (var summaryField : summary.getSummaryFields().values()) {
                if (summaryField.getName().equals(SummaryClass.DOCUMENT_ID_FIELD)) {
                    summaryField.setTransform(SummaryTransform.DOCUMENT_ID);
                }
            }
        }
    }
}
