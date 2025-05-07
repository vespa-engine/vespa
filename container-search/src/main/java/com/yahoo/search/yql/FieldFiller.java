// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import java.util.HashSet;
import java.util.Set;

import com.yahoo.component.chain.dependencies.After;
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

    private final SchemaInfo schemaInfo;
    public static final CompoundName FIELD_FILLER_DISABLE = CompoundName.from("FieldFiller.disable");

    public FieldFiller(SchemaInfo schemaInfo) {
        this.schemaInfo = schemaInfo;
    }

    @Override
    public Result search(Query query, Execution execution) {
        return execution.search(query);
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        // always fill as requested first:
        execution.fill(result, summaryClass);
        if (summaryClass == null) {
            // would fill all needed fields already
            return;
        }
        Set<String> summaryFields = result.getQuery().getPresentation().getSummaryFields();
        if (summaryFields.isEmpty() || result.getQuery().properties().getBoolean(FIELD_FILLER_DISABLE)) {
            // no special handling:
            return;
        }
        if (! summaryClass.equals(result.getQuery().getPresentation().getSummary())) {
            // some special (programmatic) fill, top-level SearchHandler will call fill again later
            return;
        }
        if (hasAll(summaryFields, summaryClass, result.getQuery().getModel().getRestrict())) {
            // no more was needed:
            return;
        }
        // fetch the requested set (using the class with all fields, confusingly named "default")
        execution.fill(result, "default");
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
