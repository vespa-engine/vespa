// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import java.util.HashMap;
import java.util.Map;

import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryField.Source;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Verifies that equally named summary fields in different summary classes don't use different fields for source.
 * The summarymap config doesn't model this.
 *
 * @author Vegard Havdal
 */
public class SummaryNamesFieldCollisions extends Processor {

    public SummaryNamesFieldCollisions(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        Map<String, Pair<String, String>> fieldToClassAndSource = new HashMap<>();
        for (DocumentSummary summary : search.getSummaries().values()) {
            if ("default".equals(summary.getName())) continue;
            for (SummaryField summaryField : summary.getSummaryFields() ) {
                if (summaryField.isImplicit()) continue;
                Pair<String, String> prevClassAndSource = fieldToClassAndSource.get(summaryField.getName());
                for (Source source : summaryField.getSources()) {
                    if (prevClassAndSource!=null) {
                        String prevClass = prevClassAndSource.getFirst();
                        String prevSource = prevClassAndSource.getSecond();
                        if ( ! prevClass.equals(summary.getName())) {
                            if ( ! prevSource.equals(source.getName())) {
                                throw new IllegalArgumentException("For search '"+ search.getName() +
                                                                   "', summary class '" + summary.getName()+"'," +
                                		                           " summary field '" + summaryField.getName() + "':" +
                                		                           " Can not use source '" + source.getName() +
                                                                   "' for this summary field, an equally named field in summary class '" +
                                                                   prevClass + "' uses a different source: '"+prevSource+"'.");
                            }
                        }
                    } else {
                        fieldToClassAndSource.put(summaryField.getName(), new Pair<>(summary.getName(), source.getName()));
                    }
                }
            }
        }
    }

}
