// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.fs4.QueryPacketData;
import com.yahoo.net.URI;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.searchlib.aggregation.FS4Hit;
import com.yahoo.searchlib.aggregation.VdsHit;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen
 */
public class HitConverterTestCase {

    private GlobalId createGlobalId(int docId) {
        return new GlobalId((new DocumentId("doc:test:" + docId)).getGlobalId());
    }

    @Test
    public void requireThatHitsAreConverted() {
        HitConverter converter = new HitConverter(new MySearcher(), new Query());
        Hit hit = converter.toSearchHit("default", new FS4Hit(1, createGlobalId(2), 3).setContext(new Hit("hit:ctx")));
        assertNotNull(hit);
        assertEquals(new URI("index:0/1/" + FastHit.asHexString(createGlobalId(2))), hit.getId());

        hit = converter.toSearchHit("default", new FS4Hit(4, createGlobalId(5), 6).setContext(new Hit("hit:ctx")));
        assertNotNull(hit);
        assertEquals(new URI("index:0/4/" + FastHit.asHexString(createGlobalId(5))), hit.getId());
    }

    @Test
    public void requireThatContextDataIsCopied() {
        Hit ctxHit = new Hit("hit:ctx");
        ctxHit.setSource("69");
        ctxHit.setSourceNumber(69);
        Query ctxQuery = new Query();
        ctxHit.setQuery(ctxQuery);

        HitConverter converter = new HitConverter(new MySearcher(), new Query());
        Hit hit = converter.toSearchHit("default", new FS4Hit(1, createGlobalId(2), 3).setContext(ctxHit));
        assertNotNull(hit);
        assertTrue(hit instanceof FastHit);
        assertEquals(1, ((FastHit)hit).getPartId());
        assertEquals(createGlobalId(2), ((FastHit)hit).getGlobalId());
        assertSame(ctxQuery, hit.getQuery());
        assertEquals(ctxHit.getSource(), hit.getSource());
        assertEquals(ctxHit.getSourceNumber(), hit.getSourceNumber());
    }

    @Test
    public void requireThatHitTagIsCopiedFromGroupingListContext() {
        QueryPacketData ctxTag = new QueryPacketData();
        GroupingListHit ctxHit = new GroupingListHit(null, null);
        ctxHit.setQueryPacketData(ctxTag);

        HitConverter converter = new HitConverter(new MySearcher(), new Query());
        Hit hit = converter.toSearchHit("default", new FS4Hit(1, createGlobalId(2), 3).setContext(ctxHit));
        assertNotNull(hit);
        assertTrue(hit instanceof FastHit);
        assertSame(ctxTag, ((FastHit)hit).getQueryPacketData());
    }

    @Test
    public void requireThatSummaryClassIsSet() {
        Searcher searcher = new MySearcher();
        HitConverter converter = new HitConverter(searcher, new Query());
        Hit hit = converter.toSearchHit("69", new FS4Hit(1, createGlobalId(2), 3).setContext(new Hit("hit:ctx")));
        assertNotNull(hit);
        assertTrue(hit instanceof FastHit);
        assertEquals("69", hit.getSearcherSpecificMetaData(searcher));
    }

    @Test
    public void requireThatHitHasContext() {
        HitConverter converter = new HitConverter(new MySearcher(), new Query());
        try {
            converter.toSearchHit("69", new FS4Hit(1, createGlobalId(2), 3));
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void requireThatUnsupportedHitClassThrows() {
        HitConverter converter = new HitConverter(new MySearcher(), new Query());
        try {
            converter.toSearchHit("69", new com.yahoo.searchlib.aggregation.Hit() {

            });
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    private static DocumentdbInfoConfig.Documentdb sixtynine() {
        DocumentdbInfoConfig.Documentdb.Builder summaryConfig = new DocumentdbInfoConfig.Documentdb.Builder();
        summaryConfig.name("none");
        summaryConfig.summaryclass(new DocumentdbInfoConfig.Documentdb.Summaryclass.Builder().id(0).name("69"));
        return new DocumentdbInfoConfig.Documentdb(summaryConfig);
    }

    @Test
    public void requireThatVdsHitCanBeConverted() {
        HitConverter converter = new HitConverter(new MySearcher(), new Query());
        GroupingListHit context = new GroupingListHit(null, new DocsumDefinitionSet(sixtynine()));
        VdsHit lowHit = new VdsHit("doc:scheme:", new byte[] { 0x55, 0x55, 0x55, 0x55 }, 1);
        lowHit.setContext(context);
        Hit hit = converter.toSearchHit("69", lowHit);
        assertNotNull(hit);
        assertTrue(hit instanceof FastHit);
        assertEquals(new Relevance(1), hit.getRelevance());
        assertTrue(hit.isFilled("69"));
    }

    private static class MySearcher extends Searcher {

        @Override
        public Result search(Query query, Execution exec) {
            return exec.search(query);
        }
    }
}
