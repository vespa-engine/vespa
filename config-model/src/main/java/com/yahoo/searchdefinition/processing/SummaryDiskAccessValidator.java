// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.derived.SummaryClass;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Optional;
import java.util.logging.Level;

import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isComplexFieldWithOnlyStructFieldAttributes;

/**
 * Emits a warning for summaries which accesses disk.
 *
 * @author bratseth
 */
public class SummaryDiskAccessValidator extends Processor {

    public SummaryDiskAccessValidator(Search search,
                                      DeployLogger deployLogger,
                                      RankProfileRegistry rankProfileRegistry,
                                      QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }


    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;
        if (documentsOnly) return;

        for (DocumentSummary summary : search.getSummaries().values()) {
            for (SummaryField summaryField : summary.getSummaryFields()) {
                for (SummaryField.Source source : summaryField.getSources()) {
                    ImmutableSDField field = search.getField(source.getName());
                    if (field == null)
                        field = findFieldProducingSummaryField(source.getName(), search).orElse(null);
                    if (field == null && ! source.getName().equals(SummaryClass.DOCUMENT_ID_FIELD))
                        throw new IllegalArgumentException(summaryField + " in " + summary + " references " +
                                                           source + ", but this field does not exist");
                    if ( ! isInMemory(field, summaryField) && ! summary.isFromDisk()) {
                        deployLogger.log(Level.WARNING, summaryField + " in " + summary + " references " +
                                                        source + ", which is not an attribute: Using this " +
                                                        "summary will cause disk accesses. " +
                                                        "Set 'from-disk' on this summary class to silence this warning.");
                    }
                }
            }
        }
    }

    private boolean isInMemory(ImmutableSDField field, SummaryField summaryField) {
        if (field == null) return false; // For DOCUMENT_ID_FIELD, which may be implicit, but is then not in memory
        if (isComplexFieldWithOnlyStructFieldAttributes(field) &&
                (summaryField.getTransform() == SummaryTransform.ATTRIBUTECOMBINER ||
                        summaryField.getTransform() == SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER)) {
            return true;
        }
        return field.doesAttributing();
    }

    private Optional<ImmutableSDField> findFieldProducingSummaryField(String name, Search search) {
        return search.allFields().filter(field -> field.getSummaryFields().get(name) != null).findAny();
    }

}
