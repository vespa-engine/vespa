// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.ImmutableImportedComplexSDField;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.stream.Stream;

/**
 * Adds the attribute summary transform ({@link SummaryTransform#ATTRIBUTE} to all {@link SummaryField} having an imported
 * field as source.
 *
 * @author bjorncs
 */
public class AddAttributeTransformToSummaryOfImportedFields extends Processor {

    public AddAttributeTransformToSummaryOfImportedFields(Search search,
                                                          DeployLogger deployLogger,
                                                          RankProfileRegistry rankProfileRegistry,
                                                          QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        search.allImportedFields()
                .forEach(field -> setTransform(field));
    }

    private Stream<SummaryField> getSummaryFieldsForImportedField(ImmutableSDField importedField) {
        return search.getSummaryFields(importedField).values().stream();
    }

    private void setTransform(ImmutableSDField field) {
        if (field instanceof ImmutableImportedComplexSDField) {
            getSummaryFieldsForImportedField(field).forEach(AddAttributeTransformToSummaryOfImportedFields::setAttributeCombinerTransform);
        } else {
            getSummaryFieldsForImportedField(field).forEach(AddAttributeTransformToSummaryOfImportedFields::setAttributeTransform);
        }
    }

    private static void setAttributeTransform(SummaryField summaryField) {
        if (summaryField.getTransform() == SummaryTransform.NONE) {
            summaryField.setTransform(SummaryTransform.ATTRIBUTE);
        }
    }

    private static void setAttributeCombinerTransform(SummaryField summaryField) {
        if (summaryField.getTransform() == SummaryTransform.MATCHED_ELEMENTS_FILTER) {
            summaryField.setTransform(SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER);
        } else {
            summaryField.setTransform(SummaryTransform.ATTRIBUTECOMBINER);
        }
    }
}
