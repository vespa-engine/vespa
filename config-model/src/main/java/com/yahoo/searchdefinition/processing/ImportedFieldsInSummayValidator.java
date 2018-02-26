// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Map;

/**
 * Validates that imported fields in document summaries are of supported types.
 * Currently, predicate fields are NOT supported.
 *
 * @author geirst
 */
public class ImportedFieldsInSummayValidator extends Processor {

    public ImportedFieldsInSummayValidator(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        if (search.importedFields().isPresent()) {
            validateDocumentSummaries(search.getSummaries());
        }
    }

    private void validateDocumentSummaries(Map<String, DocumentSummary> summaries) {
        for (DocumentSummary summary : summaries.values()) {
            for (SummaryField field : summary.getSummaryFields()) {
                ImportedField importedField = getImportedField(field);
                if (importedField != null) {
                    validateImportedSummaryField(summary, field, importedField);
                }
            }
        }
    }

    private ImportedField getImportedField(SummaryField field) {
        return search.importedFields().get().fields().get(field.getName());
    }

    private void validateImportedSummaryField(DocumentSummary summary, SummaryField field, ImportedField importedField) {
        if (field.getDataType().equals(DataType.PREDICATE)
            && importedField.targetField().getDataType().equals(DataType.PREDICATE)) {
            fail(summary, field, "Is of type predicate. Not supported in document summaries");
        }
    }

    private void fail(DocumentSummary summary, SummaryField importedField, String msg) {
        throw new IllegalArgumentException("For search '" + search.getName() + "', document summary '" + summary.getName() +
                                           "', imported summary field '" + importedField.getName() + "': " + msg);
    }
}



