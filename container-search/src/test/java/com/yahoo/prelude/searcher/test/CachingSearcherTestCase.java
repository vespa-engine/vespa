// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.component.chain.Chain;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.searcher.CachingSearcher;
import com.yahoo.prelude.searcher.DocumentSourceSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;

/**
 * Check CachingSearcher basically works.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class CachingSearcherTestCase {

    private static final String QUERY_A_NOCACHEWRITE_TRUE = "/?query=a&nocachewrite=true";
    private static final String QUERY_A = "/?query=a";
    private Chain<Searcher> searchChain;
    private DocumentSourceSearcher hits;

    @Before
    public void setUp() throws Exception {
        hits = new DocumentSourceSearcher();
        QrSearchersConfig config = new QrSearchersConfig(
                new QrSearchersConfig.Builder()
                .com(new QrSearchersConfig.Com.Builder()
                .yahoo(new QrSearchersConfig.Com.Yahoo.Builder()
                .prelude(new QrSearchersConfig.Com.Yahoo.Prelude.Builder()
                .searcher(new QrSearchersConfig.Com.Yahoo.Prelude.Searcher.Builder()
                .CachingSearcher(
                        new QrSearchersConfig.Com.Yahoo.Prelude.Searcher.CachingSearcher.Builder()
                        .cachesizemegabytes(10)
                        .maxentrysizebytes(5 * 1024 * 1024)
                        .timetoliveseconds(86400)))))));
        CachingSearcher cache = new CachingSearcher(config, Statistics.nullImplementation);
        searchChain = new Chain<>(cache, hits);
    }

    public void readyResult(String q) {
        Query query = new Query(q);
        Result r = new Result(query);
        for (int i = 0; i < 10; ++i) {
            FastHit h = new FastHit("http://127.0.0.1/" + i,
                    1.0 - ((double) i) / 10.0);
            r.hits().add(h);
        }
        hits.addResultSet(query, r);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public final void test() {
        readyResult(QUERY_A);
        Execution e = new Execution(searchChain, Execution.Context.createContextStub());
        Result r = e.search(new Query(QUERY_A));
        assertEquals(10, r.hits().getConcreteSize());
        Query query = new Query(QUERY_A);
        Result expected = new Result(query);
        hits.addResultSet(query, expected);
        e = new Execution(searchChain, Execution.Context.createContextStub());
        r = e.search(new Query(QUERY_A));
        assertEquals(10, r.hits().getConcreteSize());
        assertEquals(1, hits.getQueryCount());
    }

    @Test
    public final void testNoCacheWrite() {
        readyResult(QUERY_A_NOCACHEWRITE_TRUE);
        Execution e = new Execution(searchChain, Execution.Context.createContextStub());
        Result r = e.search(new Query(QUERY_A_NOCACHEWRITE_TRUE));
        assertEquals(10, r.hits().getConcreteSize());
        Query query = new Query(QUERY_A_NOCACHEWRITE_TRUE);
        Result expected = new Result(query);
        hits.addResultSet(query, expected);
        e = new Execution(searchChain, Execution.Context.createContextStub());
        r = e.search(new Query(QUERY_A_NOCACHEWRITE_TRUE));
        assertEquals(0, r.hits().getConcreteSize());
        assertEquals(2, hits.getQueryCount());
    }

}
