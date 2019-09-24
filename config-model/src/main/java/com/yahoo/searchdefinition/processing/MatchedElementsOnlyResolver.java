// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isComplexFieldWithOnlyStructFieldAttributes;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isSupportedComplexField;

/**
 * Iterates all summary fields with 'matched-elements-only' and adjusts transform (if all struct-fields are attributes)
 * and validates that the field type is supported.
 *
 * @author geirst
 */
public class MatchedElementsOnlyResolver extends Processor {

    public MatchedElementsOnlyResolver(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (var entry : search.getSummaries().entrySet()) {
            var summary = entry.getValue();
            for (var field : summary.getSummaryFields()) {
                if (field.getTransform().equals(SummaryTransform.MATCHED_ELEMENTS_FILTER)) {
                    processSummaryField(summary, field, validate);
                }
            }
        }
    }

    private void processSummaryField(DocumentSummary summary, SummaryField field, boolean validate) {
        var sourceField = search.getField(field.getSingleSource());
        if (sourceField != null) {
            if (isSupportedComplexField(sourceField)) {
                if (isComplexFieldWithOnlyStructFieldAttributes(sourceField)) {
                    field.setTransform(SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER);
                }
            } else if (validate) {
                fail(summary, field, "'matched-elements-only' is not supported for this field type. " +
                        "Supported field types are array of simple struct, map of primitive type to simple struct, " +
                        "and map of primitive type to primitive type");
            }
        }
        // else case is handled in SummaryFieldsMustHaveValidSource
    }

    private void fail(DocumentSummary summary, SummaryField field, String msg) {
        throw new IllegalArgumentException(formatError(search, summary, field, msg));
    }

    private String formatError(Search search, DocumentSummary summary, SummaryField field, String msg) {
        return "For search '" + search.getName() + "', document summary '" + summary.getName()
                + "', summary field '" + field.getName() + "': " + msg;
    }

}
