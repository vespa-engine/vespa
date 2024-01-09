// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.yahoo.component.chain.Chain;
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
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.testutil.DocumentSourceSearcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the FieldCollapsingSearcher class
 *
 * @author Steinar Knutsen
 */
public class FieldCollapsingSearcherTestCase {

    @Test
    void testFieldCollapsingWithoutHits() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();

        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        ZeroHitsControl checker = new ZeroHitsControl();
        chained.put(collapse, checker);

        Query q = new Query("?query=test_collapse&collapsefield=amid");
        Result r = doSearch(collapse, q, 0, 0, chained);

        assertEquals(0, r.getHitCount());
        assertNull(r.hits().getError());
        assertEquals(1, checker.queryCount);
    }

    @Test
    void testFieldCollapsingWithoutHitsHugeOffset() {
        Map<Searcher, Searcher> chained = new HashMap<>();

        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        ZeroHitsControl checker = new ZeroHitsControl();
        chained.put(collapse, checker);

        Query q = new Query("?query=test_collapse&collapsefield=amid");
        Result r = doSearch(collapse, q, 1000, 0, chained);

        assertEquals(0, r.getHitCount());
        assertNull(r.hits().getError());
        assertEquals(1, checker.queryCount);
    }

    /**
     * Tests that we do not fail on documents with missing collapsefield
     * and that they are kept in the result.
     */
    @Test
    void testFieldCollapsingWithCollapseFieldMissing() {
        Map<Searcher, Searcher> chained = new HashMap<>();

        // Set up
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHitWithoutFields("http://acme.org/a.html", 10));
        r.hits().add(createHitAmid("http://acme.org/b.html", 9, 1));
        r.hits().add(createHitWithoutFields("http://acme.org/c.html", 9));
        r.hits().add(createHitAmid("http://acme.org/d.html", 8, 2));
        r.hits().add(createHitAmid("http://acme.org/d.html", 7, 2));
        r.setTotalHitCount(5);
        docsource.addResult(q, r);

        // Test basic collapsing on amid
        q = new Query("?query=test_collapse&collapsefield=amid&collapsesize=1");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());

        assertHitWithoutFields("http://acme.org/a.html", 10, r.hits().get(0));
        assertHitAmid("http://acme.org/b.html", 9, 1, r.hits().get(1));
        assertHitWithoutFields("http://acme.org/c.html", 9, r.hits().get(2));
        assertHitAmid("http://acme.org/d.html", 8, 2, r.hits().get(3));
    }

    @Test
    void testFieldCollapsingOnMultipleFieldsWithCollapseFieldsMissing() {
        Map<Searcher, Searcher> chained = new HashMap<>();

        // Set up
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHitWithoutFields("http://acme.org/a.html", 10));  // - -
        r.hits().add(createHitBmid("http://acme.org/b.html", 9, 1));  // - 1
        r.hits().add(createHitAmid("http://acme.org/c.html", 9, 1));  // 1 -
        r.hits().add(createHitBmid("http://acme.org/d.html", 8, 1));  // - 1
        r.hits().add(createHit("http://acme.org/e.html", 8, 2, 2));  // 2 2
        r.setTotalHitCount(5);
        docsource.addResult(q, r);

        // Test basic collapsing
        q = new Query("?query=test_collapse&collapsefield=amid,bmid&collapsesize=1");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());

        assertHitWithoutFields("http://acme.org/a.html", 10, r.hits().get(0));
        assertHitBmid("http://acme.org/b.html", 9, 1, r.hits().get(1));
        assertHitAmid("http://acme.org/c.html", 9, 1, r.hits().get(2));
        assertHit("http://acme.org/e.html", 8, 2, 2, r.hits().get(3));
    }

    @Test
    void testFieldCollapsing() {
        Map<Searcher, Searcher> chained = new HashMap<>();

        // Set up
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHitAmid("http://acme.org/a.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/b.html", 9, 0));
        r.hits().add(createHitAmid("http://acme.org/c.html", 9, 1));
        r.hits().add(createHitAmid("http://acme.org/d.html", 8, 1));
        r.hits().add(createHitAmid("http://acme.org/e.html", 8, 2));
        r.hits().add(createHitAmid("http://acme.org/f.html", 7, 2));
        r.hits().add(createHitAmid("http://acme.org/g.html", 7, 3));
        r.hits().add(createHitAmid("http://acme.org/h.html", 6, 3));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        q = new Query("?query=test_collapse&collapsefield=amid");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertHitAmid("http://acme.org/a.html", 10, 0, r.hits().get(0));
        assertHitAmid("http://acme.org/c.html", 9, 1, r.hits().get(1));
        assertHitAmid("http://acme.org/e.html", 8, 2, r.hits().get(2));
        assertHitAmid("http://acme.org/g.html", 7, 3, r.hits().get(3));
    }

    /**
     * Test that collapsing works if multiple searches are necessary.
     */
    @Test
    void testFieldCollapsingTwoPhase() {
        Map<Searcher, Searcher> chained = new HashMap<>();

        // Set up
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher(1, 1.0);
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHitAmid("http://acme.org/a.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/b.html", 9, 0));
        r.hits().add(createHitAmid("http://acme.org/c.html", 9, 1));
        r.hits().add(createHitAmid("http://acme.org/d.html", 8, 1));
        r.hits().add(createHitAmid("http://acme.org/e.html", 8, 2));
        r.hits().add(createHitAmid("http://acme.org/f.html", 7, 2));
        r.hits().add(createHitAmid("http://acme.org/g.html", 7, 3));
        r.hits().add(createHitAmid("http://acme.org/h.html", 6, 3));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        q = new Query("?query=test_collapse&collapsefield=amid");
        r = doSearch(collapse, q, 0, 4, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(2, docsource.getQueryCount());
        assertHitAmid("http://acme.org/a.html", 10, 0, r.hits().get(0));
        assertHitAmid("http://acme.org/c.html", 9, 1, r.hits().get(1));
        assertHitAmid("http://acme.org/e.html", 8, 2, r.hits().get(2));
        assertHitAmid("http://acme.org/g.html", 7, 3, r.hits().get(3));
    }

    @Test
    void testNoCollapsingIfNotAskedTo() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHitAmid("http://acme.org/a.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/b.html", 9, 0));
        r.hits().add(createHitAmid("http://acme.org/c.html", 9, 1));
        r.hits().add(createHitAmid("http://acme.org/d.html", 8, 1));
        r.hits().add(createHitAmid("http://acme.org/e.html", 8, 2));
        r.hits().add(createHitAmid("http://acme.org/f.html", 7, 2));
        r.hits().add(createHitAmid("http://acme.org/g.html", 7, 3));
        r.hits().add(createHitAmid("http://acme.org/h.html", 6, 3));
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
    void testCollapsingLargeCollection() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher(4, 2.0);
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHitAmid("http://acme.org/a.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/b.html", 9, 0));
        r.hits().add(createHitAmid("http://acme.org/c.html", 9, 0));
        r.hits().add(createHitAmid("http://acme.org/d.html", 8, 0));
        r.hits().add(createHitAmid("http://acme.org/e.html", 8, 0));
        r.hits().add(createHitAmid("http://acme.org/f.html", 7, 0));
        r.hits().add(createHitAmid("http://acme.org/g.html", 7, 0));
        r.hits().add(createHitAmid("http://acme.org/h.html", 6, 0));
        r.hits().add(createHitAmid("http://acme.org/i.html", 5, 1));
        r.hits().add(createHitAmid("http://acme.org/j.html", 4, 2));
        r.setTotalHitCount(10);
        docsource.addResult(q, r);

        // Test collapsing
        q = new Query("?query=test_collapse&collapsesize=1&collapsefield=amid");
        r = doSearch(collapse, q, 0, 2, chained);

        assertEquals(2, r.getHitCount());
        assertEquals(2, docsource.getQueryCount());
        assertHitAmid("http://acme.org/a.html", 10, 0, r.hits().get(0));
        assertHitAmid("http://acme.org/i.html", 5, 1, r.hits().get(1));

        // Next results
        docsource.resetQueryCount();
        r = doSearch(collapse, q, 2, 2, chained);
        assertEquals(1, r.getHitCount());
        assertEquals(2, docsource.getQueryCount());
        assertHitAmid("http://acme.org/j.html", 4, 2, r.hits().get(0));
    }

    /**
     * Tests that collapsing hits with 2 fields works,
     * this test also shows that field order is important
     */
    @Test
    void testCollapsingWithMultipleFields() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html", 10, 1, 0));
        r.hits().add(createHit("http://acme.org/b.html", 9, 1, 1));
        r.hits().add(createHit("http://acme.org/c.html", 8, 0, 1));
        r.hits().add(createHit("http://acme.org/d.html", 7, 1, 0));
        r.setTotalHitCount(4);
        docsource.addResult(q, r);

        // Test collapsing, starting with amid
        q = new Query("?query=test_collapse&collapsesize=1&collapsefield=amid,bmid");
        r = doSearch(collapse, q, 0, 4, chained);

        assertEquals(2, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertHit("http://acme.org/a.html", 10, 1, 0, r.hits().get(0));
        assertHit("http://acme.org/c.html", 8, 0, 1, r.hits().get(1));

        docsource.resetQueryCount();

        // Test collapsing, starting with bmid
        q = new Query("?query=test_collapse&collapsesize=1&collapsefield=bmid,amid");
        r = doSearch(collapse, q, 0, 4, chained);

        assertEquals(1, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertHit("http://acme.org/a.html", 10, 1, 0, r.hits().get(0));
    }

    /**
     * Tests that using different collapse sizes for different fields works
     */
    @Test
    void testCollapsingWithMultipleFieldsAndMultipleCollapseSizes() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html", 10, 1, 1));
        r.hits().add(createHit("http://acme.org/b.html", 9, 1, 0));
        r.hits().add(createHit("http://acme.org/c.html", 9, 0, 1));
        r.hits().add(createHit("http://acme.org/d.html", 8, 1, 0));
        r.setTotalHitCount(4);
        docsource.addResult(q, r);

        // Test collapsing
        // default collapsesize is used for amid, bmid is set to 2
        q = new Query("?query=test_collapse&collapsefield=amid,bmid&collapsesize.bmid=2");
        r = doSearch(collapse, q, 0, 4, chained);

        assertEquals(2, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertHit("http://acme.org/a.html", 10, 1, 1, r.hits().get(0));
        assertHit("http://acme.org/c.html", 9, 0, 1, r.hits().get(1));
    }

    /**
     * Tests that using different collapse sizes for different fields works,
     * test that the different ways to configure collapse size have the correct precedence
     */
    @Test
    void testCollapsingWithMultipleFieldsAndMultipleCollapseSizeSources() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html", 10, 1, 1));
        r.hits().add(createHit("http://acme.org/b.html", 9, 1, 0));
        r.hits().add(createHit("http://acme.org/c.html", 9, 0, 1));
        r.hits().add(createHit("http://acme.org/d.html", 8, 1, 0));
        r.hits().add(createHit("http://acme.org/3.html", 8, 1, 0));
        r.setTotalHitCount(5);
        docsource.addResult(q, r);

        // Test collapsing
        // collapsesize 10 overwrites the default for amid & bmid
        // collapsize.bmid overwrites the collapsesize for bmid again
        q = new Query("?query=test_collapse&collapsesize=10&collapsefield=amid,bmid&collapsesize.bmid=2");
        r = doSearch(collapse, q, 0, 5, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertHit("http://acme.org/a.html", 10, 1, 1, r.hits().get(0));
        assertHit("http://acme.org/b.html", 9, 1, 0, r.hits().get(1));
        assertHit("http://acme.org/c.html", 9, 0, 1, r.hits().get(2));
        assertHit("http://acme.org/d.html", 8, 1, 0, r.hits().get(3));
    }

    /**
     * Tests that collapsing on multiple fields works if we have to search multiple
     * time to get enough hits
     */
    @Test
    void testCollapsingOnMoreFieldsWithManySimilarFieldValues() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher(4, 1.0);
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHit("http://acme.org/a.html", 10, 0, 1, 1)); // first hit
        r.hits().add(createHit("http://acme.org/b.html", 9, 0, 1, 2));
        r.hits().add(createHit("http://acme.org/c.html", 9, 0, 6, 2)); // - - 1. search: 1
        r.hits().add(createHit("http://acme.org/d.html", 8, 0, 6, 3));
        r.hits().add(createHit("http://acme.org/e.html", 8, 0, 6, 3));
        r.hits().add(createHit("http://acme.org/f.html", 7, 0, 6, 3)); // - - 1. search: 2
        r.hits().add(createHit("http://acme.org/g.html", 7, 0, 1, 1));
        r.hits().add(createHit("http://acme.org/h.html", 6, 1, 1, 1));
        r.hits().add(createHit("http://acme.org/i.html", 5, 2, 2, 1)); // - - 1. search: 3
        r.hits().add(createHit("http://acme.org/j.html", 4, 3, 3, 2)); // 3rd hit, cmid new
        r.hits().add(createHit("http://acme.org/k.html", 4, 3, 4, 3));
        r.hits().add(createHit("http://acme.org/l.html", 4, 3, 5, 3)); // - - 1. search: 4
        r.hits().add(createHit("http://acme.org/m.html", 4, 4, 6, 3)); // 4th hit, amid new
        r.hits().add(createHit("http://acme.org/n.html", 4, 4, 7, 4));
        r.setTotalHitCount(14);
        docsource.addResult(q, r);

        // Test collapsing
        q = new Query("?query=test_collapse&collapsesize=1&collapsefield=amid,bmid,cmid");
        r = doSearch(collapse, q, 0, 2, chained);

        assertEquals(2, r.getHitCount());
        assertEquals(4, docsource.getQueryCount());
        assertHit("http://acme.org/a.html", 10, 0, 1, 1, r.hits().get(0));
        assertHit("http://acme.org/j.html", 4, 3, 3, 2, r.hits().get(1));

        // Next results
        docsource.resetQueryCount();
        r = doSearch(collapse, q, 2, 2, chained);
        assertEquals(1, r.getHitCount());
        assertEquals(3, docsource.getQueryCount());
        assertHit("http://acme.org/m.html", 4, 4, 6, 3, r.hits().get(0));
    }

    /**
     * Tests collapsing of "messy" data
     */
    @Test
    void testCollapsingDispersedCollection() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher(1, 2.0);
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse");
        Result r = new Result(q);
        r.hits().add(createHitAmid("http://acme.org/a.html", 10, 1));
        r.hits().add(createHitAmid("http://acme.org/b.html", 10, 1));
        r.hits().add(createHitAmid("http://acme.org/c.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/d.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/e.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/f.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/g.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/h.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/i.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/j.html", 10, 1));
        r.setTotalHitCount(10);
        docsource.addResult(q, r);

        // Test collapsing
        q = new Query("?query=test_collapse&collapse=true&collapsefield=amid");
        r = doSearch(collapse, q, 0, 3, chained);

        assertEquals(2, r.getHitCount());
        assertHitAmid("http://acme.org/a.html", 10, 1, r.hits().get(0));
        assertHitAmid("http://acme.org/c.html", 10, 0, r.hits().get(1));
    }

    @Test
    void testQueryTransformAndCollapsing() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        Searcher messUp = new QueryMessupSearcher();

        chained.put(collapse, messUp);
        chained.put(messUp, docsource);

        Query q = new Query("?query=%22test%20collapse%22+b&collapsefield=amid&type=all");
        Result r = new Result(q);
        r.hits().add(createHitAmid("http://acme.org/a.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/b.html", 9, 0));
        r.hits().add(createHitAmid("http://acme.org/c.html", 9, 0));
        r.hits().add(createHitAmid("http://acme.org/d.html", 8, 0));
        r.hits().add(createHitAmid("http://acme.org/e.html", 8, 0));
        r.hits().add(createHitAmid("http://acme.org/f.html", 7, 0));
        r.hits().add(createHitAmid("http://acme.org/g.html", 7, 0));
        r.hits().add(createHitAmid("http://acme.org/h.html", 6, 1));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        q = new Query("?query=%22test%20collapse%22&collapsefield=amid&type=all");
        r = doSearch(collapse, q, 0, 2, chained);

        assertEquals(2, docsource.getQueryCount());
        assertEquals(2, r.getHitCount());
        assertHitAmid("http://acme.org/a.html", 10, 0, r.hits().get(0));
        assertHitAmid("http://acme.org/h.html", 6, 1, r.hits().get(1));
    }

    @Test
    void testFieldCollapsingTwoPhaseSelectSummary() {
        // Set up
        Map<Searcher, Searcher> chained = new HashMap<>();
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(collapse, docsource);

        Query q = new Query("?query=test_collapse&summary=placeholder");
        Result r = new Result(q);
        r.hits().add(createHitAmid("http://acme.org/a.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/b.html", 9, 0));
        r.hits().add(createHitAmid("http://acme.org/c.html", 9, 1));
        r.hits().add(createHitAmid("http://acme.org/d.html", 8, 1));
        r.hits().add(createHitAmid("http://acme.org/e.html", 8, 2));
        r.hits().add(createHitAmid("http://acme.org/f.html", 7, 2));
        r.hits().add(createHitAmid("http://acme.org/g.html", 7, 3));
        r.hits().add(createHitAmid("http://acme.org/h.html", 6, 3));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        q = new Query("?query=test_collapse&collapsefield=amid&summary=placeholder");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertTrue(r.isFilled("placeholder"));
        assertHitAmid("http://acme.org/a.html", 10, 0, r.hits().get(0));
        assertHitAmid("http://acme.org/c.html", 9, 1, r.hits().get(1));
        assertHitAmid("http://acme.org/e.html", 8, 2, r.hits().get(2));
        assertHitAmid("http://acme.org/g.html", 7, 3, r.hits().get(3));

        docsource.resetQueryCount();
        // Test basic collapsing on mid
        q = new Query("?collapse.summary=short&query=test_collapse&collapsefield=amid&summary=placeholder");
        r = doSearch(collapse, q, 0, 10, chained);

        assertEquals(4, r.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertFalse(r.isFilled("placeholder"));
        assertTrue(r.isFilled("short"));
        assertHitAmid("http://acme.org/a.html", 10, 0, r.hits().get(0));
        assertHitAmid("http://acme.org/c.html", 9, 1, r.hits().get(1));
        assertHitAmid("http://acme.org/e.html", 8, 2, r.hits().get(2));
        assertHitAmid("http://acme.org/g.html", 7, 3, r.hits().get(3));
    }

    @Test
    void testFieldCollapsingWithGrouping() {
        // Set up
        FieldCollapsingSearcher collapse = new FieldCollapsingSearcher();
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        Chain<Searcher> chain = new Chain<>(collapse, new AddAggregationStyleGroupingResultSearcher(), docsource);

        Query q = new Query("?query=test_collapse");

        Result r = new Result(q);
        r.hits().add(createHitAmid("http://acme.org/a.html", 10, 0));
        r.hits().add(createHitAmid("http://acme.org/b.html", 9, 0));
        r.hits().add(createHitAmid("http://acme.org/c.html", 9, 1));
        r.hits().add(createHitAmid("http://acme.org/d.html", 8, 1));
        r.hits().add(createHitAmid("http://acme.org/e.html", 8, 2));
        r.hits().add(createHitAmid("http://acme.org/f.html", 7, 2));
        r.hits().add(createHitAmid("http://acme.org/g.html", 7, 3));
        r.hits().add(createHitAmid("http://acme.org/h.html", 6, 3));
        r.setTotalHitCount(8);
        docsource.addResult(q, r);

        // Test basic collapsing on mid
        Query query = new Query("?query=test_collapse&collapsefield=amid");
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);

        // Assert that the regular hits are collapsed
        assertEquals(4 + 1, result.getHitCount());
        assertEquals(1, docsource.getQueryCount());
        assertHitAmid("http://acme.org/a.html", 10, 0, result.hits().get(0));
        assertHitAmid("http://acme.org/c.html", 9, 1, result.hits().get(1));
        assertHitAmid("http://acme.org/e.html", 8, 2, result.hits().get(2));
        assertHitAmid("http://acme.org/g.html", 7, 3, result.hits().get(3));

        // Assert that the aggregation group hierarchy is left intact
        HitGroup root = getFirstGroupIn(result.hits());
        assertNotNull(root);
        assertEquals("group:root:", root.getId().stringValue().substring(0, 11)); // The id ends by a global counter currently
        assertEquals(1, root.size());
        HitGroup groupList = (GroupList) root.get("grouplist:g1");
        assertNotNull(groupList);
        assertEquals(1, groupList.size());
        HitGroup group = (HitGroup) groupList.get("group:long:37");
        assertNotNull(group);
    }

    private Group getFirstGroupIn(HitGroup hits) {
        for (Hit h : hits)
            if (h instanceof Group) return (Group)h;
        return null;
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits, Map<Searcher, Searcher> chained) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher, chained).search(query);
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain, Map<Searcher, Searcher> chained) {
        List<Searcher> searchers = new ArrayList<>();
        for (Searcher current = topOfChain; current != null; current = chained.get(current))
            searchers.add(current);
        return new Chain<>(searchers);
    }

    private Execution createExecution(Searcher searcher, Map<Searcher, Searcher> chained) {
        return new Execution(chainedAsSearchChain(searcher, chained), Execution.Context.createContextStub());
    }

    /**
     * Simulates the return when grouping is used for aggregation purposes and there is a plain hit list in addition:
     * The returned result contains both regular hits at the top level (from non-grouping)
     * and groups contained aggregation information.
     */
    private static class AddAggregationStyleGroupingResultSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Result r = execution.search(query);
            r.hits().add(createAggregationGroup("g1"));
            return r;
        }

        private HitGroup createAggregationGroup(String label) {
            Group root = new Group(new RootId(0), new Relevance(1));
            GroupList groupList = new GroupList(label);
            root.add(groupList);
            Group value = new Group(new LongId(37L), new Relevance(2.11));
            groupList.add(value);
            return root;
        }
    }

    private FastHit createHitWithoutFields(String uri, int relevancy) {
        return new FastHit(uri,relevancy);
    }

    private FastHit createHitAmid(String uri,int relevancy,int amid) {
        FastHit hit = new FastHit(uri,relevancy);
        hit.setField("amid", String.valueOf(amid));
        return hit;
    }

    private FastHit createHitBmid(String uri,int relevancy,int bmid) {
        FastHit hit = new FastHit(uri,relevancy);
        hit.setField("bmid", String.valueOf(bmid));
        return hit;
    }

    private FastHit createHit(String uri,int relevancy,int amid,int bmid) {
        FastHit hit = new FastHit(uri,relevancy);
        hit.setField("amid", String.valueOf(amid));
        hit.setField("bmid", String.valueOf(bmid));
        return hit;
    }

    private FastHit createHit(String uri,int relevancy,int amid,int bmid,int cmid) {
        FastHit hit = new FastHit(uri,relevancy);
        hit.setField("amid", String.valueOf(amid));
        hit.setField("bmid", String.valueOf(bmid));
        hit.setField("cmid", String.valueOf(cmid));
        return hit;
    }

    private void assertHitWithoutFields(String uri,int relevancy,Hit hit) {
        assertEquals(uri,hit.getId().toString());
        assertEquals(relevancy, ((int) hit.getRelevance().getScore()));
        assertTrue(hit.fields().isEmpty());
    }

    private void assertHitAmid(String uri, int relevancy, int amid, Hit hit) {
        assertEquals(uri,hit.getId().toString());
        assertEquals(relevancy, ((int) hit.getRelevance().getScore()));
        assertEquals(amid,Integer.parseInt((String) hit.getField("amid")));
    }

    private void assertHitBmid(String uri, int relevancy, int bmid, Hit hit) {
        assertEquals(uri,hit.getId().toString());
        assertEquals(relevancy, ((int) hit.getRelevance().getScore()));
        assertEquals(bmid,Integer.parseInt((String) hit.getField("bmid")));
    }

    private void assertHit(String uri,int relevancy,int amid,int bmid,Hit hit) {
        assertHitAmid(uri,relevancy,amid,hit);
        assertEquals(bmid,Integer.parseInt((String) hit.getField("bmid")));
    }

    private void assertHit(String uri,int relevancy,int amid,int bmid,int cmid,Hit hit) {
        assertHitAmid(uri,relevancy,amid,hit);
        assertHitBmid(uri,relevancy,bmid,hit);
        assertEquals(cmid,Integer.parseInt((String) hit.getField("cmid")));
    }

    private static class ZeroHitsControl extends com.yahoo.search.Searcher {

        public int queryCount = 0;

        @Override
        public Result search(Query query, Execution execution) {
            ++queryCount;
            if (query.getHits() == 0) {
                return new Result(query);
            } else {
                return new Result(query, ErrorMessage.createIllegalQuery("Did not request zero hits."));
            }
        }
    }

    public static class QueryMessupSearcher extends Searcher {

        @Override
        public Result search(com.yahoo.search.Query query, Execution execution) {
            AndItem a = new AndItem();
            a.addItem(query.getModel().getQueryTree().getRoot());
            WordItem item = new WordItem("b");
            item.setFromQuery(true);
            a.addItem(item);

            query.getModel().getQueryTree().setRoot(a);

            return execution.search(query);
        }

    }

}
