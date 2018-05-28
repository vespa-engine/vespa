// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import java.util.List;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.Sorting.FieldOrder;
import com.yahoo.search.searchchain.Execution;

/**
 * Avoid doing relevance calculations if sorting only
 * on attributes.
 *
 * @author Steinar Knutsen
 */
@After("rawQuery")
@Before("transformedQuery")
public class NoRankingSearcher extends Searcher {

    private static final String RANK = "[rank]";
    private static final String UNRANKED = "unranked";

    @Override
    public Result search(Query query, Execution execution) {
        List<FieldOrder> s = (query.getRanking().getSorting() != null) ? query.getRanking().getSorting().fieldOrders() : null;
        if (s == null) {
            return execution.search(query);
        }
        for (FieldOrder f : s) {
            if (RANK.equals(f.getFieldName())) {
                return execution.search(query);
            }
        }
        query.getRanking().setProfile(UNRANKED);
        return execution.search(query);
    }

}
