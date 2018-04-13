// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.Hit;
import com.yahoo.prelude.searcher.QuerySnapshotSearcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class QuerySnapshotSearcherTestCase {

    @Test
    public void test() {
        Searcher searcher=new QuerySnapshotSearcher();
        Result result = doSearch(searcher, new Query(), 0,10);
        Hit hit=result.hits().get(0);
        assertEquals(String.valueOf(Double.POSITIVE_INFINITY),
                     hit.getRelevance().toString());
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        Execution.Context context = new Execution.Context(null, null, null, new RendererRegistry(MoreExecutors.directExecutor()), new SimpleLinguistics());
        return new Execution(chainedAsSearchChain(searcher), context);
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
