// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.annotations.Beta;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * Remove fields which are not explicitly requested, if any field is explicitly
 * requested. Disable using FieldFilter.disable=true in request.
 *
 * @author Steinar Knutsen
 */
@Beta
@After(MinimalQueryInserter.EXTERNAL_YQL)
@Before("com.yahoo.search.yql.FieldFiller")
public class FieldFilter extends Searcher {

    public static final CompoundName FIELD_FILTER_DISABLE = new CompoundName("FieldFilter.disable");

    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        filter(result);
        return result;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        execution.fill(result, summaryClass);
        filter(result);
    }

    private void filter(Result result) {
        Set<String> requestedFields;

        if (result.getQuery().properties().getBoolean(FIELD_FILTER_DISABLE)) return;
        if (result.getQuery().getPresentation().getSummaryFields().isEmpty()) return;

        requestedFields = result.getQuery().getPresentation().getSummaryFields();
        for (Iterator<Hit> i = result.hits().unorderedDeepIterator(); i.hasNext();) {
            Hit h = i.next();
            if (h.isMeta()) continue;
            for (Iterator<Entry<String, Object>> fields = h.fieldIterator(); fields.hasNext();) {
                Entry<String, Object> field = fields.next();
                if ( ! requestedFields.contains(field.getKey()))
                    fields.remove();
            }

        }
    }

}
