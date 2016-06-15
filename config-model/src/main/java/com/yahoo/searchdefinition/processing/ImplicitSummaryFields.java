// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * This processor adds all implicit summary fields to all registered document summaries. If another field has already
 * been registered with one of the implicit names, this processor will throw an {@link IllegalStateException}.
 */
public class ImplicitSummaryFields extends Processor {

    public ImplicitSummaryFields(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process() {
        for (DocumentSummary docsum : search.getSummaries().values()) {
            addField(docsum, new SummaryField("rankfeatures", DataType.STRING, SummaryTransform.RANKFEATURES));
            addField(docsum, new SummaryField("summaryfeatures", DataType.STRING, SummaryTransform.SUMMARYFEATURES));
        }
    }

    private void addField(DocumentSummary docsum, SummaryField field) {
        if (docsum.getSummaryField(field.getName()) != null) {
            throw new IllegalStateException("Summary class '" + docsum.getName() + "' uses reserved field name '" +
                                            field.getName() + "'.");
        }
        docsum.add(field);
    }
}
