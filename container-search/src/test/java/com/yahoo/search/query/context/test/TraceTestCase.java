// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.context.test;

import com.yahoo.search.Query;
import com.yahoo.search.query.context.QueryContext;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Steinar Knutsen
 */
public class TraceTestCase {

    @Test
    public void testBasicTracing() {
        Query query=new Query();
        QueryContext h = query.getContext(true);
        h.trace("first message", 0);
        h.trace("second message", 0);
        assertEquals("trace: [ [ first message second message ] ]", h.toString());
    }

    @Test
    public void testCloning() throws IOException {
        Query query=new Query();
        QueryContext h = query.getContext(true);
        h.trace("first message", 0);
        QueryContext h2 = query.clone().getContext(true);
        h2.trace("second message", 0);
        QueryContext h3 = query.clone().getContext(true);
        h3.trace("third message", 0);
        h.trace("fourth message", 0);
        h2.trace("fifth message", 0);
        Writer w = new StringWriter();
        Writer w2 = new StringWriter();
        h2.render(w2);
        String finishedBelow = w2.toString();
        h.render(w);
        String toplevel = w.toString();
        // check no matter which QueryContext ends up in the final Result,
        // all context info is rendered
        assertEquals(toplevel, finishedBelow);
        // basic sanity test
        assertEquals("trace: [ [ " +
                     "first message second message third message " +
                     "fourth message fifth message ] ]",h.toString());
        Iterator<String> i = h.getTrace().traceNode().root().descendants(String.class).iterator();
        assertEquals("first message",i.next());
        assertEquals("second message",i.next());
        assertEquals("third message",i.next());
        assertEquals("fourth message",i.next());
        assertEquals("fifth message",i.next());
    }

    @Test
    public void testEmpty() throws IOException {
        Query query=new Query();
        QueryContext h = query.getContext(true);
        Writer w = new StringWriter();
        h.render(w);
        assertEquals("", w.toString());
    }

    @Test
    public void testEmptySubSequence() {
        Query query=new Query();
        QueryContext h = query.getContext(true);
        query.clone().getContext(true);
        Writer w = new StringWriter();
        try {
            h.render(w);
        } catch (IOException e) {
            assertTrue("rendering empty subsequence crashed", false);
        }
    }

    @Test
    public void testAttachedTraces() throws IOException {
        String needle0 = "nalle";
        String needle1 = "tralle";
        String needle2 = "trolle";
        String needle3 = "bamse";
        Query q = new Query("/?tracelevel=1");
        q.trace(needle0, false, 1);
        Query q2 = new Query();
        q.attachContext(q2);
        q2.trace(needle1, false, 1);
        q2.trace(needle2, false, 1);
        q.trace(needle3, false, 1);
        Writer w = new StringWriter();
        q.getContext(false).render(w);
        String trace = w.toString();
        int nalle = trace.indexOf(needle0);
        int tralle = trace.indexOf(needle1);
        int trolle = trace.indexOf(needle2);
        int bamse = trace.indexOf(needle3);
        assertTrue("Could not find first manual context to main query.", nalle > 0);
        assertTrue("Could not find second manual context to main query.", bamse > 0);
        assertTrue("Could not find first manual context to attached query.", tralle > 0);
        assertTrue("Could not find second manual context to attached query.", trolle > 0);
    }

}
