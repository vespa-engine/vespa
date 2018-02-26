// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Class that processes reference fields and removes attribute aspect of such fields from summary.
 *
 * A document summary for a reference field should always be fetched from the document instance in back-end
 * as the attribute vector does not store the original document id string.
 *
 * @author geirst
 */
public class ReferenceFieldsProcessor extends Processor {

    public ReferenceFieldsProcessor(Search search,
                                    DeployLogger deployLogger,
                                    RankProfileRegistry rankProfileRegistry,
                                    QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        clearSummaryAttributeAspectForConcreteFields();
        clearSummaryAttributeAspectForExplicitSummaryFields();
    }

    private void clearSummaryAttributeAspectForExplicitSummaryFields() {
        for (DocumentSummary docSum : search.getSummaries().values()) {
            docSum.getSummaryFields().stream()
                    .filter(summaryField  -> summaryField.getDataType() instanceof ReferenceDataType)
                    .forEach(summaryField -> summaryField.setTransform(SummaryTransform.NONE));
        }
    }

    private void clearSummaryAttributeAspectForConcreteFields() {
        for (SDField field : search.allConcreteFields()) {
            if (field.getDataType() instanceof ReferenceDataType) {
                removeFromAttributePrefetchSummaryClass(field);
                clearSummaryTransformOnSummaryFields(field);
            }
        }
    }

    private void removeFromAttributePrefetchSummaryClass(SDField field) {
        DocumentSummary summary = search.getSummary("attributeprefetch");
        if (summary != null) {
            summary.remove(field.getName());
        }
    }

    private void clearSummaryTransformOnSummaryFields(SDField field) {
        search.getSummaryFields(field).values().forEach(summaryField -> summaryField.setTransform(SummaryTransform.NONE));
    }

}

