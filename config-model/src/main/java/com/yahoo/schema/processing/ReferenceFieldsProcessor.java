// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import static com.yahoo.prelude.fastsearch.VespaBackEndSearcher.SORTABLE_ATTRIBUTES_SUMMARY_CLASS;

/**
 * Class that processes reference fields and removes attribute aspect of such fields from summary.
 *
 * A document summary for a reference field should always be fetched from the document instance in back-end
 * as the attribute vector does not store the original document id string.
 *
 * @author geirst
 */
public class ReferenceFieldsProcessor extends Processor {

    public ReferenceFieldsProcessor(Schema schema,
                                    DeployLogger deployLogger,
                                    RankProfileRegistry rankProfileRegistry,
                                    QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        clearSummaryAttributeAspectForConcreteFields();
        clearSummaryAttributeAspectForExplicitSummaryFields();
    }

    private void clearSummaryAttributeAspectForExplicitSummaryFields() {
        for (DocumentSummary docSum : schema.getSummaries().values()) {
            docSum.getSummaryFields().values().stream()
                    .filter(summaryField  -> summaryField.getDataType() instanceof NewDocumentReferenceDataType)
                    .forEach(summaryField -> summaryField.setTransform(SummaryTransform.NONE));
        }
    }

    private void clearSummaryAttributeAspectForConcreteFields() {
        for (SDField field : schema.allConcreteFields()) {
            if (field.getDataType() instanceof NewDocumentReferenceDataType) {
                removeFromAttributePrefetchSummaryClass(field);
                clearSummaryTransformOnSummaryFields(field);
            }
        }
    }

    private void removeFromAttributePrefetchSummaryClass(SDField field) {
        DocumentSummary summary = schema.getSummariesInThis().get(SORTABLE_ATTRIBUTES_SUMMARY_CLASS);
        if (summary != null) {
            summary.remove(field.getName());
        }
    }

    private void clearSummaryTransformOnSummaryFields(SDField field) {
        schema.getSummaryFields(field).forEach(summaryField -> summaryField.setTransform(SummaryTransform.NONE));
    }

}

