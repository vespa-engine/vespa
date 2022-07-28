// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.statistics.ElapsedTimeTestCase;
import com.yahoo.search.statistics.ElapsedTimeTestCase.CreativeTimeSource;
import com.yahoo.search.statistics.ElapsedTimeTestCase.UselessSearcher;
import com.yahoo.search.statistics.TimeTracker;
import com.yahoo.text.Utf8;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Check the legacy sync default renderer doesn't spontaneously combust.
 *
 * @author Steinar Knutsen
 */
public class SyncDefaultRendererTestCase {

    SyncDefaultRenderer d;

    @BeforeEach
    public void setUp() throws Exception {
        d = new SyncDefaultRenderer();
        d.init();
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    final void testGetEncoding() {
        assertEquals("utf-8", d.getEncoding());
    }

    @Test
    final void testGetMimeType() {
        assertEquals("text/xml", d.getMimeType());
    }

    @Test
    void testRenderWriterResult() throws InterruptedException, ExecutionException {
        Query q = new Query("/?query=a&tracelevel=5");
        q.getPresentation().setTiming(true);
        Result r = new Result(q);
        r.setCoverage(new Coverage(500, 1));

        TimeTracker t = new TimeTracker(new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third")));
        ElapsedTimeTestCase.doInjectTimeSource(t, new CreativeTimeSource(
                new long[]{1L, 2L, 3L, 4L, 5L, 6L, 7L}));
        t.sampleSearch(0, true);
        t.sampleSearch(1, true);
        t.sampleSearch(2, true);
        t.sampleSearch(3, true);
        t.sampleSearchReturn(2, true, null);
        t.sampleSearchReturn(1, true, null);
        t.sampleSearchReturn(0, true, null);
        r.getElapsedTime().add(t);
        FastHit h = new FastHit("http://localhost/", .95);
        h.setField("$a", "Hello, world.");
        h.setField("b", "foo");
        r.hits().add(h);
        HitGroup g = new HitGroup("usual");
        h = new FastHit("http://localhost/1", .90);
        h.setField("c", "d");
        g.add(h);
        r.hits().add(g);
        HitGroup gg = new HitGroup("type grouphit");
        gg.types().add("grouphit");
        gg.setField("e", "f");
        r.hits().add(gg);
        r.hits().addError(ErrorMessage.createInternalServerError("boom"));
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        CompletableFuture<Boolean> f = d.renderResponse(bs, r, null, null);
        assertTrue(f.get());
        String summary = Utf8.toString(bs.toByteArray());
        // TODO figure out a reasonably strict and reasonably flexible way to test
        assertTrue(summary.length() > 900);
    }

}
