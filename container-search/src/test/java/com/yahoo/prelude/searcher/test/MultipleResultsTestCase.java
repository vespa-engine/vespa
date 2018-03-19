// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.searcher.MultipleResultsSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test of MultipleResultsSearcher
 *
 * @author tonytv
 */
@SuppressWarnings("deprecation")
public class MultipleResultsTestCase {

    private DocumentSourceSearcher docSource;

    private MultipleResultsSearcher searcher;

    private Chain<Searcher> chain;

    @Before
    protected void setUp() {
        docSource=new DocumentSourceSearcher();
        searcher=new MultipleResultsSearcher();
        chain=new Chain<>("multipleresultschain",searcher,docSource);
    }

    @Test
    public void testRetrieveHeterogenousHits() {
        Query query = createQuery();

        Result originalResult = new Result(query);
        int n1 = 15, n2 = 25, n3 = 25, n4=25;
        addHits(originalResult, "others", n1);
        addHits(originalResult, "music", n2);
        addHits(originalResult, "movies", n3);
        addHits(originalResult, "others", n4);
        originalResult.setTotalHitCount(n1 + n2 + n3 + n4);

        docSource.addResult(query, originalResult);

        query.setWindow(0,30);
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);

        HitGroup musicGroup = (HitGroup)result.hits().get("music");
        HitGroup moviesGroup = (HitGroup)result.hits().get("movies");

        assertEquals( 15, musicGroup.size() );
        assertEquals( 15, moviesGroup.size() );
        assertEquals( 3, docSource.getQueryCount() );
    }

    @Test
    public void testRetrieveHitsForGroup() {
        Query query = createQuery();

        Result originalResult = new Result(query);
        int n1 = 200, n2=30;
        addHits(originalResult, "music", n1, 1000);
        addHits(originalResult, "movies", n2, 100);
        originalResult.setTotalHitCount(n1 + n2);

        docSource.addResult(query, originalResult);

        Query restrictedQuery = createQuery("movies");
        Result restrictedResult = new Result(restrictedQuery);
        addHits(restrictedResult, "movies", n2, 100);
        restrictedResult.setTotalHitCount(n2);

        docSource.addResult(restrictedQuery, restrictedResult);

        query.setWindow(0,30);
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);

        HitGroup musicGroup = (HitGroup)result.hits().get("music");
        HitGroup moviesGroup = (HitGroup)result.hits().get("movies");

        assertEquals( 15, musicGroup.size());
        assertEquals( 15, moviesGroup.size());
    }

    @Test
    public void testNoHitsForResultSet() {
        Query query = createQuery();

        Result originalResult = new Result(query);
        int n1 = 20;
        int n2 = 100;
        addHits(originalResult, "music", n1);
        addHits(originalResult, "other", n2);
        originalResult.setTotalHitCount(n1 + n2);

        docSource.addResult(query, originalResult);

        query.setWindow(0,30);
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);

        HitGroup musicGroup = (HitGroup)result.hits().get("music");
        HitGroup moviesGroup = (HitGroup)result.hits().get("movies");

        assertEquals( 15, musicGroup.size());
        assertEquals( 0, moviesGroup.size());
    }

    private void addHits(Result result, String docName, int numHits,
                         int baseRelevancy) {
        for (int i=0; i<numHits; ++i) {
            result.hits().add(createHit("foo" + i,
                                    baseRelevancy - i,
                                    docName));
        }
    }

    private void addHits(Result result, String docName, int numHits) {
        addHits(result, docName, numHits, 1000);
    }


    private FastHit createHit(String uri, int relevancy, String docName) {
        FastHit hit = new FastHit(uri,relevancy);
        hit.setField(Hit.SDDOCNAME_FIELD, docName);
        return hit;
    }

    private Query createQuery() {
        return new Query("?query=foo&" +
            "multipleresultsets.numHits=music:15,movies:15&" +
            "multipleresultsets.additionalHitsFactor=0.8&" +
            "multipleresultsets.maxTimesRetrieveHeterogeneousHits=3");
    }

    private Query createQuery(String restrictList) {
        Query query = createQuery();
        query.getModel().setRestrict(restrictList);
        return query;
    }

}
