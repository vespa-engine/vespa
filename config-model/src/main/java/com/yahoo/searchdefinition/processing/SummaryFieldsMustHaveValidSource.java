// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Verifies that the source fields actually refers to a valid field.
 *
 * @author baldersheim
 *
 */
public class SummaryFieldsMustHaveValidSource extends Processor {

    SummaryFieldsMustHaveValidSource(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (DocumentSummary summary : search.getSummaries().values()) {
            for (SummaryField summaryField : summary.getSummaryFields()) {
                if (summaryField.getSources().isEmpty()) {
                    if ((summaryField.getTransform() != SummaryTransform.RANKFEATURES) &&
                        (summaryField.getTransform() != SummaryTransform.SUMMARYFEATURES))
                    {
                        verifySource(summaryField.getName(), summaryField, summary);
                    }
                } else if (summaryField.getSourceCount() == 1) {
                    verifySource(summaryField.getSingleSource(), summaryField, summary);
                } else {
                    for (SummaryField.Source source : summaryField.getSources()) {
                        if ( ! source.getName().equals(summaryField.getName()) ) {
                            verifySource(source.getName(), summaryField, summary);
                        }
                    }
                }
            }
        }

    }

    private boolean isValid(String source, SummaryField summaryField, DocumentSummary summary) {
        return  isDocumentField(source) ||
                (isNotInThisSummaryClass(summary, source) && isSummaryField(source)) ||
                (isInThisSummaryClass(summary, source) && !source.equals(summaryField.getName())) ||
                ("documentid".equals(source));
    }

    private void verifySource(String source, SummaryField summaryField, DocumentSummary summary) {
        if ( ! isValid(source, summaryField, summary) ) {
            throw new IllegalArgumentException("For search '" + search.getName() + "', summary class '" +
                                               summary.getName() + "'," + " summary field '" + summaryField.getName() +
                                               "': there is no valid source '" + source + "'.");
        }
    }

    private static boolean isNotInThisSummaryClass(DocumentSummary summary, String name) {
        return summary.getSummaryField(name) == null;
    }

    private static boolean isInThisSummaryClass(DocumentSummary summary, String name) {
        return summary.getSummaryField(name) != null;
    }

    private boolean isDocumentField(String name) {
        return search.getField(name) != null;
    }

    private boolean isSummaryField(String name) {
        return search.getSummaryField(name) != null;
    }

}
