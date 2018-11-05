// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.rendering;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.util.concurrent.ListenableFuture;
import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.statistics.ElapsedTimeTestCase;
import com.yahoo.search.statistics.TimeTracker;
import com.yahoo.search.statistics.ElapsedTimeTestCase.CreativeTimeSource;
import com.yahoo.search.statistics.ElapsedTimeTestCase.UselessSearcher;
import com.yahoo.text.Utf8;

/**
 * Test the XML renderer
 *
 * @author Steinar Knutsen
 */
public class XMLRendererTestCase {

    XmlRenderer d;

    @Before
    public void setUp() throws Exception {
        d = new XmlRenderer();
        d.init();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public final void testGetEncoding() {
        assertEquals("utf-8", d.getEncoding());
    }

    @Test
    public final void testGetMimeType() {
        assertEquals("text/xml", d.getMimeType());
    }

    @Test
    public final void testImplicitDefaultRender() throws Exception {
        Query q = new Query("/?query=a&tracelevel=5&reportCoverage=true");
        q.getPresentation().setTiming(true);
        Result r = new Result(q);
        r.setCoverage(new Coverage(500, 1));

        TimeTracker t = new TimeTracker(new Chain<Searcher>(
                new UselessSearcher("first"), new UselessSearcher("second"),
                new UselessSearcher("third")));
        ElapsedTimeTestCase.doInjectTimeSource(t, new CreativeTimeSource(
                new long[] { 1L, 2L, 3L, 4L, 5L, 6L, 7L }));
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
        ListenableFuture<Boolean> f = d.render(bs, r, null, null);
        assertTrue(f.get());
        String summary = Utf8.toString(bs.toByteArray());

        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "<result total-hit-count=\"0\"",
                summary.substring(0, 67)
        );
        assertTrue(summary.contains("<meta type=\"context\">"));
        assertTrue(summary.contains("<error code=\"18\">Internal server error.</error>"));
        assertTrue(summary.contains("<hit type=\"grouphit\" relevancy=\"1.0\">"));
        assertTrue(summary.contains("<hit type=\"summary\" relevancy=\"0.95\">"));
        assertEquals(2, occurrences("<error ", summary));
        assertTrue(summary.length() > 1000);
    }

    private int occurrences(String fragment, String string) {
        int occurrences = 0;
        int cursor = 0;
        while ( -1 != (cursor = string.indexOf(fragment, cursor))) {
            occurrences++;
            cursor += fragment.length();
        }
        return occurrences;
    }

}
