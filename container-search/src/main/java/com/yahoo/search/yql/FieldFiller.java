// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.Beta;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig.Documentdb;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig.Documentdb.Summaryclass;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig.Documentdb.Summaryclass.Fields;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.Presentation;
import com.yahoo.search.searchchain.Execution;

/**
 * Ensure the fields specified in {@link Presentation#getSummaryFields()} are
 * available after filling phase.
 *
 * @author stiankri
 * @author Steinar Knutsen
 */
@Beta
@After(MinimalQueryInserter.EXTERNAL_YQL)
public class FieldFiller extends Searcher {

    private final Set<String> intersectionOfAttributes;
    private final SummaryIntersections summaryDb = new SummaryIntersections();
    public static final CompoundName FIELD_FILLER_DISABLE = new CompoundName("FieldFiller.disable");

    private static class SummaryIntersections {
        private final Map<String, Map<String, Set<String>>> db = new HashMap<>();

        void add(String dbName, Summaryclass summary) {
            Map<String, Set<String>> docType = getOrCreateDocType(dbName);
            Set<String> fields = new HashSet<>(summary.fields().size());
            for (Fields f : summary.fields()) {
                fields.add(f.name());
            }
            docType.put(summary.name(), fields);
        }

        private Map<String, Set<String>> getOrCreateDocType(String dbName) {
            Map<String, Set<String>> docType = db.get(dbName);
            if (docType == null) {
                docType = new HashMap<>();
                db.put(dbName, docType);
            }
            return docType;
        }

        boolean hasAll(Set<String> requested, String summaryName, Set<String> restrict) {
            Set<String> explicitRestriction;
            Set<String> intersection = null;

            if (restrict.isEmpty()) {
                explicitRestriction = db.keySet();
            } else {
                explicitRestriction = restrict;
            }

            for (String docType : explicitRestriction) {
                Map<String, Set<String>> summaries = db.get(docType);
                Set<String> summary;

                if (summaries == null) {
                    continue;
                }
                summary = summaries.get(summaryName);
                if (summary == null) {
                    intersection = null;
                    break;
                }
                if (intersection == null) {
                    intersection = new HashSet<>(summary.size());
                    intersection.addAll(summary);
                } else {
                    intersection.retainAll(summary);
                }
            }
            return intersection != null && intersection.containsAll(requested);
        }
    }

    public FieldFiller(DocumentdbInfoConfig config) {
        intersectionOfAttributes = new HashSet<>();
        boolean first = true;

        for (Documentdb db : config.documentdb()) {
            for (Summaryclass summary : db.summaryclass()) {
                Set<String> attributes;
                if (Execution.ATTRIBUTEPREFETCH.equals(summary.name())) {
                    attributes = new HashSet<>(summary.fields().size());
                    for (Fields f : summary.fields()) {
                        attributes.add(f.name());
                    }
                    if (first) {
                        first = false;
                        intersectionOfAttributes.addAll(attributes);
                    } else {
                        intersectionOfAttributes.retainAll(attributes);
                    }
                }
                // yes, we store attribute prefetch here as well, this is in
                // case we get a query where we have a restrict parameter which
                // makes filling with attribute prefetch possible even though it
                // wouldn't have been possible without restricting the set of
                // doctypes
                summaryDb.add(db.name(), summary);
            }
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        return execution.search(query);
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        execution.fill(result, summaryClass);

        Set<String> summaryFields = result.getQuery().getPresentation().getSummaryFields();

        if (summaryFields.isEmpty() || summaryClass == null ||
            result.getQuery().properties().getBoolean(FIELD_FILLER_DISABLE)) {
            return;
        }

        if (intersectionOfAttributes.containsAll(summaryFields)) {
            if ( ! Execution.ATTRIBUTEPREFETCH.equals(summaryClass)) {
                execution.fill(result, Execution.ATTRIBUTEPREFETCH);
            }
        } else {
            // Yes, summaryClass may be Execution.ATTRIBUTEPREFETCH here
            if ( ! summaryDb.hasAll(summaryFields, summaryClass, result.getQuery().getModel().getRestrict())) {
                execution.fill(result, null);
            }
        }
    }

}
