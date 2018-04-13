// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.Hit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import com.yahoo.prelude.searcher.QueryValidatingSearcher;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests correct denial of query.
 *
 * @author Steinar Knutsen
 */
public class QueryValidatingSearcherTestCase {

    @Test
    public void testBasic() {
        // Setup
        Map<Searcher, Searcher> chained = new HashMap<>();
        Query query = new Query("?query=test");

        Result result = new Result(query);
        result.hits().add(new Hit("ymail://1111111111/AQAAAP7JgwEAj6XjQQAAAO/+ggA=",950));

        Searcher validator = new QueryValidatingSearcher();
        DocumentSourceSearcher source = new DocumentSourceSearcher();
        chained.put(validator, source);
        source.addResult(query, result);

        // Exercise
        Result returnedResult = doSearch(validator, query, 0, 10, chained);

        // Validate
        assertEquals(1, returnedResult.getHitCount());
        assertNull(returnedResult.hits().getError());

        returnedResult = doSearch(validator, query, 0, 1001, chained);
        assertEquals(0, returnedResult.getConcreteHitCount());
        assertEquals(4, returnedResult.hits().getError().getCode());

        returnedResult = doSearch(validator, query, 1001, 10, chained);
        assertEquals(0, returnedResult.getConcreteHitCount());
        assertEquals(4, returnedResult.hits().getError().getCode());
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits, Map<Searcher, Searcher> chained) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher, chained).search(query);
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain, Map<Searcher, Searcher> chained) {
        List<Searcher> searchers = new ArrayList<>();
        for (Searcher current = topOfChain; current != null; current = chained.get(current)) {
            searchers.add(current);
        }
        return new Chain<>(searchers);
    }

    private Execution createExecution(Searcher searcher, Map<Searcher, Searcher> chained) {
        Execution.Context context = new Execution.Context(null, null, null, new RendererRegistry(MoreExecutors.directExecutor()), new SimpleLinguistics());
        return new Execution(chainedAsSearchChain(searcher, chained), context);
    }

}
