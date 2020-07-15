// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.prelude.searcher.PosSearcher;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the PosSearcher
 *
 * @author Gunnar Gauslaa Bergem
 */
@SuppressWarnings("deprecation")
public class PosSearcherTestCase {

    private final PosSearcher searcher = new PosSearcher();

    /**
     * Tests basic lat/long input.
     */
    @Test
    public void testBasics() {
        Query q = new Query();
        q.properties().set("pos.ll", "N0;E0");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,0,0,450668,0,1,0,4294967295)", q.getRanking().getLocation().toString());

        q = new Query();
        q.properties().set("pos.ll", "N60;E30");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,30000000,60000000,450668,0,1,0,2147483647)", q.getRanking().getLocation().toString());
    }

    /**
     * Tests basic bounding box input.
     */
    @Test
    public void testBoundingBox() {
        Query q = new Query();
        q.properties().set("pos.bb", "n=51.9,s=50.2,e=0.34,w=-10.01");
        doSearch(searcher, q, 0, 10);
        assertEquals("[2,-10010000,50200000,340000,51900000]",
                     q.getRanking().getLocation().toString());

        q = new Query();
        q.properties().set("pos.bb", "n=0,s=0,e=123.456789,w=-123.456789");
        doSearch(searcher, q, 0, 10);
        assertEquals("[2,-123456789,0,123456789,0]",
                     q.getRanking().getLocation().toString());

        q = new Query();
        q.properties().set("pos.bb", "n=12.345678,s=-12.345678,e=0,w=0");
        doSearch(searcher, q, 0, 10);
        assertEquals("[2,0,-12345678,0,12345678]",
                     q.getRanking().getLocation().toString());

        q = new Query();
        q.properties().set("pos.bb", "n=0.000001,s=-0.000001,e=0.000001,w=-0.000001");
        doSearch(searcher, q, 0, 10);
        assertEquals("[2,-1,-1,1,1]",
                     q.getRanking().getLocation().toString());
    }

    /**
     * Tests basic bounding box input.
     */
    @Test
    public void testBoundingBoxAndRadius() {
        Query q = new Query();
        q.properties().set("pos.bb", "n=60.111,s=59.999,e=30.111,w=29.999");
        q.properties().set("pos.ll", "N60;E30");
        doSearch(searcher, q, 0, 10);
        assertEquals(
                "[2,29999000,59999000,30111000,60111000]" +
                "(2,30000000,60000000,450668,0,1,0,2147483647)",
                q.getRanking().getLocation().toString());
    }

    /**
     * Tests different ways of specifying the radius.
     */
    @Test
    public void testRadiusUnits() {
        Query q = new Query();
        q.properties().set("pos.ll", "N0;E0");
        q.properties().set("pos.radius", "2km");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,0,0,18026,0,1,0,4294967295)", q.getRanking().getLocation().toString());

        q = new Query();
        q.properties().set("pos.ll", "N0;E0");
        q.properties().set("pos.radius", "2000m");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,0,0,18026,0,1,0,4294967295)", q.getRanking().getLocation().toString());

        q = new Query();
        q.properties().set("pos.ll", "N0;E0");
        q.properties().set("pos.radius", "20mi");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,0,0,290112,0,1,0,4294967295)", q.getRanking().getLocation().toString());

        q = new Query();
        q.properties().set("pos.ll", "N0;E0");
        q.properties().set("pos.radius", "2km");
        q.properties().set("pos.units", "udeg");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,0,0,18026,0,1,0,4294967295)", q.getRanking().getLocation().toString());

        q = new Query();
        q.properties().set("pos.ll", "N0;E0");
        q.properties().set("pos.radius", "-1");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,0,0,-1,0,1,0,4294967295)", q.getRanking().getLocation().toString());
        assertEquals("(2,0,0,536870912,0,1,0,4294967295)", q.getRanking().getLocation().backendString());
    }

    /**
     * Tests xy position (internal format).
     */
    @Test
    public void testXY() {
        Query q = new Query();
        q.properties().set("pos.xy", "22500;22500");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,22500,22500,450668,0,1,0)", q.getRanking().getLocation().toString());

        q = new Query();
        q.properties().set("pos.xy", "22500;22500");
        q.properties().set("pos.units", "unknown");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,22500,22500,450668,0,1,0)", q.getRanking().getLocation().toString());
    }

    @Test
    public void testNotOverridingOldStyleParameters() {
        PosSearcher searcher = new PosSearcher();
        Query q = new Query("?query=test&pos.ll=N10.15;E6.08&location=(2,-1100222,0,300,0,1,0,CalcLatLon)");
        q.setTraceLevel(1);
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,-1100222,0,300,0,1,0,4294967295)", q.getRanking().getLocation().toString());
    }

    /**
     * Tests input parameters that should report errors.
     */
    @Test
    public void testInvalidInput() {
        PosSearcher searcher = new PosSearcher();
        Result result;

        Query q = new Query();
        q.properties().set("pos.ll", "NE74.14;E14.48");
        result = doSearch(searcher, q, 0, 10);
        assertEquals("Error in pos parameters: Unable to parse lat/long string 'NE74.14;E14.48': already set direction once, cannot add direction: E",
        ((ErrorHit)result.hits().get(0)).errors().iterator().next().getDetailedMessage());

        q = new Query();
        q.properties().set("pos.ll", "NE74.14;E14.48");
        q.properties().set("pos.xy", "82400, 72800");
        result = doSearch(searcher, q, 0, 10);
        assertEquals("Error in pos parameters: Cannot handle both lat/long and xy coords at the same time",
            ((ErrorHit)result.hits().get(0)).errors().iterator().next().getDetailedMessage());
    }

    @Test
    public void testWrappingTheNorthPole() {
        Query q = new Query();
        q.properties().set("pos.ll", "N89.9985365158;E122.163600102");
        q.properties().set("pos.radius", "20mi");
        doSearch(searcher, q, 0, 10);
        assertEquals("(2,122163600,89998536,290112,0,1,0,109743)", q.getRanking().getLocation().toString());
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
