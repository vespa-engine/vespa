// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.templates.test.TilingTestCase;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.Hit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests that rank features are rendered when requested in the query
 *
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class RankFeatureDumpTestCase {

    private static final String rankFeatureString=
            "{\"match.weight.as1\":10,\"attribute(ai1)\":1.000000,\"proximity(as1, 1, 2)\":2.000000}";

    @Test
    public void test() throws IOException {
        Query query=new Query("?query=five&rankfeatures");
        assertTrue(query.getRanking().getListFeatures()); // New api
        Result result = doSearch(new MockBackend(), query, 0,10);
        assertTrue(TilingTestCase.getRendered(result).contains(
                "<field name=\"" + com.yahoo.search.result.Hit.RANKFEATURES_FIELD + "\">" + rankFeatureString + "</field>"));
    }

    private static class MockBackend extends Searcher {

        @Override
        public Result search(com.yahoo.search.Query query, Execution execution) {
            Result result=new Result(query);
            Hit hit=new FastHit("test",1000);
            hit.setField(com.yahoo.search.result.Hit.RANKFEATURES_FIELD,rankFeatureString);
            result.hits().add(hit);
            return result;
        }

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
