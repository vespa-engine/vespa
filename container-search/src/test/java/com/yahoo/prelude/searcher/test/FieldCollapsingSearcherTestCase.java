// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.searcher.FieldCollapsingSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.LongId;
import com.yahoo.search.grouping.result.RootId;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the FieldCollapsingSearcher class
 *
 * @author Steinar Knutsen
 */
@SuppressWarnings("deprecation")
public class FieldCollapsingSearcherTestCase {

    private FastHit createHit(String uri,int relevancy,int mid) {
        FastHit hit = new FastHit(uri,relevancy);
        hit.setField("amid", String.valueOf(mid));
        return hit;
    }

    private void assertHit(String uri,int relevancy,int mid,Hit hit) {
        assertEquals(uri,hit.getId().toString());
        assertEquals(relevancy, ((int) hit.getRelevance().getScore()));
        assertEquals(mid,Integer.parseInt((String) hit.getField("amid")));
    }

    private static class ZeroHitsControl extends com.yahoo.search.Searcher {
        public int queryCount = 0;
        public com.yahoo.search.Result search(com.yahoo.search.Query query,
                com.yahoo.search.searchchain.Execution execution) {
            ++queryCount;
            if (query.getHits() == 0) {
                return new Result(query);
            } else {
                return new Result(query, ErrorMessage.createIllegalQuery("Did not request zero hits."));
            }
        }
    }

    @Test
    public void testFieldCollapsingWithoutHits() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();

        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher("other");
        ZeroHitsControl checker = new ZeroHitsControl();
        chained.put(collapse, checker);

        Query q = new Query("?query=test_collapse&collapsefield=amid");
        Result r = doSearch(collapse, q, 0, 0, chained);

        assertEquals(0, r.getHitCount());
        assertNull(r.hits().getError());
        assertEquals(1, checker.queryCount);
    }

    @Test
    public void testFieldCollapsingWithoutHitsHugeOffset() {
        Map<Searcher, Searcher> chained = new HashMap<>();

        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher("other");
        ZeroHitsControl checker = new ZeroHitsControl();
        chained.put(collapse, checker);

        Query q = new Query("?query=test_collapse&collapsefield=amid");
        Result r = doSearch(collapse, q, 1000, 0, chained);

        assertEquals(0, r.getHitCount());
        assertNull(r.hits().getError());
        assertEquals(1, checker.queryCount);
    }

    @Test
    public void testFieldCollapsing() {
        Map<Searcher, Searcher> chained = new HashMap<>();

        // Set up
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher("other");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        // Caveat: Collapse is set to false, because that's what the
        // collapser asks for
        Query q = new Query("?query=test_collapse&collapsefield=amid");
        // The searcher turns off collapsing further on in the chain
        q.properties().set("collapse", "0");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html",10,0));
        r.hits().add(createHit("http://acme.org/b.html", 9,0));
        r.hits().add(createHit("http://acme.org/c.html", 9,1));
        r.hits().add(createHit("http://acme.org/d.html", 8,1));
        r.hits().add(createHit("http://acme.org/e.html", 8,2));
        r.hits().add(createHit("http://acme.org/f.html", 7,2));
        r.hits().add(createHit("http://acme.org/g.html", 7,3));
        r.hits().add(createHit("http://acme.org/h.html", 6,3));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        q = new Query("?query=test_collapse&collapsefield=amid");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertHit("http://acme.org/a.html",10,0,r.hits().get(0));
        assertHit("http://acme.org/c.html", 9,1,r.hits().get(1));
        assertHit("http://acme.org/e.html", 8,2,r.hits().get(2));
        assertHit("http://acme.org/g.html", 7,3,r.hits().get(3));
    }

    @Test
    public void testFieldCollapsingTwoPhase() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher("other");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);
        // Caveat: Collapse is set to false, because that's what the
        // collapser asks for
        Query q = new Query("?query=test_collapse&collapsefield=amid");
        // The searcher turns off collapsing further on in the chain
        q.properties().set("collapse", "0");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html",10,0));
        r.hits().add(createHit("http://acme.org/b.html", 9,0));
        r.hits().add(createHit("http://acme.org/c.html", 9,1));
        r.hits().add(createHit("http://acme.org/d.html", 8,1));
        r.hits().add(createHit("http://acme.org/e.html", 8,2));
        r.hits().add(createHit("http://acme.org/f.html", 7,2));
        r.hits().add(createHit("http://acme.org/g.html", 7,3));
        r.hits().add(createHit("http://acme.org/h.html", 6,3));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        q = new Query("?query=test_collapse&collapsefield=amid");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertHit("http://acme.org/a.html",10,0,r.hits().get(0));
        assertHit("http://acme.org/c.html", 9,1,r.hits().get(1));
        assertHit("http://acme.org/e.html", 8,2,r.hits().get(2));
        assertHit("http://acme.org/g.html", 7,3,r.hits().get(3));
    }

    @Test
    public void testNoCollapsingIfNotAskedTo() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html",10,0));
        r.hits().add(createHit("http://acme.org/b.html", 9,0));
        r.hits().add(createHit("http://acme.org/c.html", 9,1));
        r.hits().add(createHit("http://acme.org/d.html", 8,1));
        r.hits().add(createHit("http://acme.org/e.html", 8,2));
        r.hits().add(createHit("http://acme.org/f.html", 7,2));
        r.hits().add(createHit("http://acme.org/g.html", 7,3));
        r.hits().add(createHit("http://acme.org/h.html", 6,3));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test that no collapsing occured
        q = new Query("?query=test_collapse");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(8, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
    }

    /**
     * Tests that collapsing many hits from one site works, and without
     * an excessive number of backend requests
     */
    @Test
    public void testCollapsingLargeCollection() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher(4,2.0,"amid");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse&collapsesize=1&collapsefield=amid");
        // The searcher turns off collapsing further on in the chain
        q.properties().set("collapse", "0");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html",10,0));
        r.hits().add(createHit("http://acme.org/b.html", 9,0));
        r.hits().add(createHit("http://acme.org/c.html", 9,0));
        r.hits().add(createHit("http://acme.org/d.html", 8,0));
        r.hits().add(createHit("http://acme.org/e.html", 8,0));
        r.hits().add(createHit("http://acme.org/f.html", 7,0));
        r.hits().add(createHit("http://acme.org/g.html", 7,0));
        r.hits().add(createHit("http://acme.org/h.html", 6,0));
        r.hits().add(createHit("http://acme.org/i.html", 5,1));
        r.hits().add(createHit("http://acme.org/j.html", 4,2));
        r.setTotalHitCount(10);
        docsource.addResult(q, r);

        // Test collapsing
        q = new Query("?query=test_collapse&collapsesize=1&collapsefield=amid");
        r = doSearch(collapse, q, 0, 2, chained);

        assertEquals(2, r.getHitCount());
        assertEquals(2, docsource.getQueryCount());
        assertHit("http://acme.org/a.html",10,0,r.hits().get(0));
        assertHit("http://acme.org/i.html", 5,1,r.hits().get(1));

        // Next results
        docsource.resetQueryCount();
        r = doSearch(collapse, q, 2, 2, chained);
        assertEquals(1, r.getHitCount());
        assertEquals(2, docsource.getQueryCount());
        assertHit("http://acme.org/j.html",4,2,r.hits().get(0));
    }

    /**
     * Tests collapsing of "messy" data
     */
    @Test
    public void testCollapsingDispersedCollection() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher(1,2.0,"amid");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse&collapse=true&collapsefield=amid");
        // The searcher turns off collapsing further on in the chain
        q.properties().set("collapse", "0");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html",10,1));
        r.hits().add(createHit("http://acme.org/b.html",10,1));
        r.hits().add(createHit("http://acme.org/c.html",10,0));
        r.hits().add(createHit("http://acme.org/d.html",10,0));
        r.hits().add(createHit("http://acme.org/e.html",10,0));
        r.hits().add(createHit("http://acme.org/f.html",10,0));
        r.hits().add(createHit("http://acme.org/g.html",10,0));
        r.hits().add(createHit("http://acme.org/h.html",10,0));
        r.hits().add(createHit("http://acme.org/i.html",10,0));
        r.hits().add(createHit("http://acme.org/j.html",10,1));
        r.setTotalHitCount(10);
        docsource.addResult(q, r);

        // Test collapsing
        q = new Query("?query=test_collapse&collapse=true&collapsefield=amid");
        r = doSearch(collapse, q, 0, 3, chained);

        assertEquals(2, r.getHitCount());
        assertHit("http://acme.org/a.html",10,1,r.hits().get(0));
        assertHit("http://acme.org/c.html",10,0,r.hits().get(1));
    }

    public static class QueryMessupSearcher extends Searcher {
        public Result search(com.yahoo.search.Query query, Execution execution) {
            AndItem a = new AndItem();
            a.addItem(query.getModel().getQueryTree().getRoot());
            a.addItem(new WordItem("b"));
            query.getModel().getQueryTree().setRoot(a);

            return execution.search(query);
        }
    }

    @Test
    public void testQueryTransformAndCollapsing() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher("other");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        Searcher messUp = new QueryMessupSearcher();

        chained.put(collapse, messUp);
        chained.put(messUp, docsource);

        // Caveat: Collapse is set to false, because that's what the
        // collapser asks for
        Query q = new Query("?query=test_collapse+b&collapsefield=amid");
        // The searcher turns off collapsing further on in the chain
        q.properties().set("collapse", "0");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html",10,0));
        r.hits().add(createHit("http://acme.org/b.html", 9,0));
        r.hits().add(createHit("http://acme.org/c.html", 9,0));
        r.hits().add(createHit("http://acme.org/d.html", 8,0));
        r.hits().add(createHit("http://acme.org/e.html", 8,0));
        r.hits().add(createHit("http://acme.org/f.html", 7,0));
        r.hits().add(createHit("http://acme.org/g.html", 7,0));
        r.hits().add(createHit("http://acme.org/h.html", 6,1));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        q = new Query("?query=test_collapse&collapsefield=amid");
        r = doSearch(collapse, q, 0, 2, chained);

        assertEquals(2, docsource.getQueryCount());
        assertEquals(2, r.getHitCount());
        assertHit("http://acme.org/a.html",10,0,r.hits().get(0));
        assertHit("http://acme.org/h.html", 6,1,r.hits().get(1));
    }

    // This test depends on DocumentSourceSearcher filling the hits
    // with whatever data it got, ignoring actual summary arguments
    // in the fill call, then saying the hits are filled for the
    // ignored argument. Rewrite to contain different summaries if
    // DocumentSourceSearcher gets extended.
    @Test
    public void testFieldCollapsingTwoPhaseSelectSummary() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher("other");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);
        // Caveat: Collapse is set to false, because that's what the
        // collapser asks for
        Query q = new Query("?query=test_collapse&collapsefield=amid&summary=placeholder");
        // The searcher turns off collapsing further on in the chain
        q.properties().set("collapse", "0");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html",10,0));
        r.hits().add(createHit("http://acme.org/b.html", 9,0));
        r.hits().add(createHit("http://acme.org/c.html", 9,1));
        r.hits().add(createHit("http://acme.org/d.html", 8,1));
        r.hits().add(createHit("http://acme.org/e.html", 8,2));
        r.hits().add(createHit("http://acme.org/f.html", 7,2));
        r.hits().add(createHit("http://acme.org/g.html", 7,3));
        r.hits().add(createHit("http://acme.org/h.html", 6,3));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        q = new Query("?query=test_collapse&collapsefield=amid&summary=placeholder");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertTrue(r.isFilled("placeholder"));
        assertHit("http://acme.org/a.html",10,0,r.hits().get(0));
        assertHit("http://acme.org/c.html", 9,1,r.hits().get(1));
        assertHit("http://acme.org/e.html", 8,2,r.hits().get(2));
        assertHit("http://acme.org/g.html", 7,3,r.hits().get(3));

        docsource.resetQueryCount();
        // Test basic collapsing on mid
        q = new Query("?collapse.summary=short&query=test_collapse&collapsefield=amid&summary=placeholder");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertFalse(r.isFilled("placeholder"));
        assertTrue(r.isFilled("short"));
        assertHit("http://acme.org/a.html",10,0,r.hits().get(0));
        assertHit("http://acme.org/c.html", 9,1,r.hits().get(1));
        assertHit("http://acme.org/e.html", 8,2,r.hits().get(2));
        assertHit("http://acme.org/g.html", 7,3,r.hits().get(3));
    }

    @Test
    public void testFieldCollapsingWithGrouping() {
        // Set up
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher("other");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        Chain<Searcher> chain=new Chain<>(collapse,new AddAggregationStyleGroupingResultSearcher(),docsource);

        // Caveat: Collapse is set to false, because that's what the
        // collapser asks for
        Query q = new Query("?query=test_collapse&collapsefield=amid");
        // The searcher turns off collapsing further on in the chain
        q.properties().set("collapse", "0");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html",10,0));
        r.hits().add(createHit("http://acme.org/b.html", 9,0));
        r.hits().add(createHit("http://acme.org/c.html", 9,1));
        r.hits().add(createHit("http://acme.org/d.html", 8,1));
        r.hits().add(createHit("http://acme.org/e.html", 8,2));
        r.hits().add(createHit("http://acme.org/f.html", 7,2));
        r.hits().add(createHit("http://acme.org/g.html", 7,3));
        r.hits().add(createHit("http://acme.org/h.html", 6,3));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        Query query = new Query("?query=test_collapse&collapsefield=amid");
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);

        // Assert that the regular hits are collapsed
        assertEquals(4+1, result.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertHit("http://acme.org/a.html",10,0,result.hits().get(0));
        assertHit("http://acme.org/c.html", 9,1,result.hits().get(1));
        assertHit("http://acme.org/e.html", 8,2,result.hits().get(2));
        assertHit("http://acme.org/g.html", 7,3,result.hits().get(3));

        // Assert that the aggregation group hierarchy is left intact
        HitGroup root= getFirstGroupIn(result.hits());
        assertNotNull(root);
        assertEquals("group:root:",root.getId().stringValue().substring(0,11)); // The id ends by a global counter currently
        assertEquals(1,root.size());
        HitGroup groupList= (GroupList)root.get("grouplist:g1");
        assertNotNull(groupList);
        assertEquals(1,groupList.size());
        HitGroup group= (HitGroup)groupList.get("group:long:37");
        assertNotNull(group);
    }

    private Group getFirstGroupIn(HitGroup hits) {
        for (Hit h : hits) {
            if (h instanceof Group) return (Group)h;
        }
        return null;
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

    /**
     * Simulates the return when grouping is used for aggregation purposes and there is a plain hit list in addition:
     * The returned result contains both regular hits at the top level (from non-grouping)
     * and groups contained aggregation information.
     */
    private static class AddAggregationStyleGroupingResultSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result r=execution.search(query);
            r.hits().add(createAggregationGroup("g1"));
            return r;
        }

        private HitGroup createAggregationGroup(String label) {
            Group root = new Group(new RootId(0), new Relevance(1));
            GroupList groupList = new GroupList(label);
            root.add(groupList);
            Group value=new Group(new LongId(37l),new Relevance(2.11));
            groupList.add(value);
            return root;
        }
    }

}
