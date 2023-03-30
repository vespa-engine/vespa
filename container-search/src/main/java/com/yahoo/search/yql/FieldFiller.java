// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import java.util.HashSet;
import java.util.Set;

import com.yahoo.component.chain.dependencies.After;
import static com.yahoo.prelude.fastsearch.VespaBackEndSearcher.SORTABLE_ATTRIBUTES_SUMMARY_CLASS;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.Presentation;
import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;

/**
 * Ensure the fields specified in {@link Presentation#getSummaryFields()} are available after filling phase.
 *
 * @author stiankri
 * @author Steinar Knutsen
 */
@After(MinimalQueryInserter.EXTERNAL_YQL)
public class FieldFiller extends Searcher {

    private final Set<String> intersectionOfAttributes;
    private final SchemaInfo schemaInfo;
    public static final CompoundName FIELD_FILLER_DISABLE = CompoundName.from("FieldFiller.disable");

    public FieldFiller(SchemaInfo schemaInfo) {
        this.schemaInfo = schemaInfo;

        intersectionOfAttributes = new HashSet<>();
        boolean first = true;
        for (Schema schema : schemaInfo.schemas().values()) {
            for (DocumentSummary summary : schema.documentSummaries().values()) {
                Set<String> attributes;
                if (SORTABLE_ATTRIBUTES_SUMMARY_CLASS.equals(summary.name())) {
                    attributes = new HashSet<>(summary.fields().size());
                    for (DocumentSummary.Field f : summary.fields().values()) {
                        attributes.add(f.name());
                    }
                    if (first) {
                        first = false;
                        intersectionOfAttributes.addAll(attributes);
                    } else {
                        intersectionOfAttributes.retainAll(attributes);
                    }
                }
            }
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        return execution.search(query);
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        Set<String> summaryFields = result.getQuery().getPresentation().getSummaryFields();
        if (summaryFields.isEmpty() || result.getQuery().properties().getBoolean(FIELD_FILLER_DISABLE)) {
            // no special handling:
            execution.fill(result, summaryClass);
            return;
        }
        if (summaryClass != null) {
            // always fill requested class:
            execution.fill(result, summaryClass);
            if (hasAll(summaryFields, summaryClass, result.getQuery().getModel().getRestrict())) {
                // no more was needed:
                return;
            }
        }
        // we need more:
        if (intersectionOfAttributes.containsAll(summaryFields)) {
            // only attributes needed:
            execution.fill(result, SORTABLE_ATTRIBUTES_SUMMARY_CLASS);
        } else {
            // fetch all summary fields:
            execution.fill(result, null);
        }
    }

    private boolean hasAll(Set<String> requested, String summaryName, Set<String> restrict) {
        Set<String> intersection = null;
        for (String schemaName : restrict.isEmpty() ? schemaInfo.schemas().keySet() : restrict) {
            Schema schema = schemaInfo.schemas().get(schemaName);
            if (schema == null) continue;

            DocumentSummary summary = schema.documentSummaries().get(summaryName);
            if (summary == null) {
                intersection = null;
                break;
            }
            if (intersection == null) {
                intersection = new HashSet<>(summary.fields().size());
                intersection.addAll(summary.fields().keySet());
            } else {
                intersection.retainAll(summary.fields().keySet());
            }
        }
        return intersection != null && intersection.containsAll(requested);
    }

}
