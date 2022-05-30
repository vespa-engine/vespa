// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * This processor adds all implicit summary fields to all registered document summaries. If another field has already
 * been registered with one of the implicit names, this processor will throw an {@link IllegalStateException}.
 */
public class ImplicitSummaryFields extends Processor {

    public ImplicitSummaryFields(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (DocumentSummary docsum : schema.getSummariesInThis().values()) {
            if (docsum.inherited().isPresent()) continue; // Implicit fields are added to inheriting summaries through their parent
            addField(docsum, new SummaryField("rankfeatures", DataType.STRING, SummaryTransform.RANKFEATURES), validate);
            addField(docsum, new SummaryField("summaryfeatures", DataType.STRING, SummaryTransform.SUMMARYFEATURES), validate);
        }
    }

    private void addField(DocumentSummary docsum, SummaryField field, boolean validate) {
        if (validate && docsum.getSummaryField(field.getName()) != null) {
            throw new IllegalArgumentException("Summary class '" + docsum.getName() + "' uses reserved field name '" +
                                               field.getName() + "'.");
        }
        docsum.add(field);
    }

}
