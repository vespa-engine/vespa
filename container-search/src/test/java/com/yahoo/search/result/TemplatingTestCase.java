// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.yahoo.search.rendering.Renderer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Splitter;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.templates.UserTemplate;
import com.yahoo.prelude.templates.test.BoomTemplate;
import com.yahoo.search.Query;
import com.yahoo.search.Result;

/**
 * Control helper method for result rendering/result templates.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class TemplatingTestCase {
    Result result;

    @Before
    public void setUp() throws Exception {
        Query q = new Query("/?query=a&presentation.format=nalle&offset=1&hits=5");
        result = new Result(q);
        result.setTotalHitCount(1000L);
        result.hits().add(new FastHit("http://localhost/1", .95));
        result.hits().add(new FastHit("http://localhost/2", .90));
        result.hits().add(new FastHit("http://localhost/3", .85));
        result.hits().add(new FastHit("http://localhost/4", .80));
        result.hits().add(new FastHit("http://localhost/5", .75));
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public final void testGetFirstHitNo() {
        assertEquals(2, result.getTemplating().getFirstHitNo());
    }

    @Test
    public final void testGetNextFirstHitNo() {
        assertEquals(7, result.getTemplating().getNextFirstHitNo());
        result.getQuery().setHits(6);
        assertEquals(0, result.getTemplating().getNextFirstHitNo());
    }

    @Test
    public final void testGetNextLastHitNo() {
        assertEquals(11, result.getTemplating().getNextLastHitNo());
        result.getQuery().setHits(6);
        assertEquals(0, result.getTemplating().getNextLastHitNo());
    }

    @Test
    public final void testGetLastHitNo() {
        assertEquals(6, result.getTemplating().getLastHitNo());
    }

    @Test
    public final void testGetPrevFirstHitNo() {
        assertEquals(1, result.getTemplating().getPrevFirstHitNo());
    }

    @Test
    public final void testGetPrevLastHitNo() {
        assertEquals(1, result.getTemplating().getPrevLastHitNo());
    }

    @Test
    public final void testGetNextResultURL() {
        String next = result.getTemplating().getNextResultURL();
        Set<String> expectedParameters = new HashSet<>(Arrays.asList(new String[] {
                "hits=5",
                "query=a",
                "presentation.format=nalle",
                "offset=6"
        }));
        Set<String> actualParameters = new HashSet<>();
        Splitter s = Splitter.on("&");
        for (String parameter : s.split(next.substring(next.indexOf('?') + 1))) {
            actualParameters.add(parameter);
        }
        assertEquals(expectedParameters, actualParameters);
    }

    @Test
    public final void testGetPreviousResultURL() {
        String previous = result.getTemplating().getPreviousResultURL();
        Set<String> expectedParameters = new HashSet<>(Arrays.asList(new String[] {
                "hits=5",
                "query=a",
                "presentation.format=nalle",
                "offset=0"
        }));
        Set<String> actualParameters = new HashSet<>();
        Splitter s = Splitter.on("&");
        for (String parameter : s.split(previous.substring(previous.indexOf('?') + 1))) {
            actualParameters.add(parameter);
        }
        assertEquals(expectedParameters, actualParameters);
    }

    @Test
    public final void testGetCurrentResultURL() {
        String previous = result.getTemplating().getCurrentResultURL();
        Set<String> expectedParameters = new HashSet<>(Arrays.asList(new String[] {
                "hits=5",
                "query=a",
                "presentation.format=nalle",
                "offset=1"
        }));
        Set<String> actualParameters = new HashSet<>();
        Splitter s = Splitter.on("&");
        for (String parameter : s.split(previous.substring(previous.indexOf('?') + 1))) {
            actualParameters.add(parameter);
        }
        assertEquals(expectedParameters, actualParameters);
    }

    @Test
    public final void testGetTemplates() {
        @SuppressWarnings({ "unchecked", "deprecation" })
        UserTemplate<Writer> t = result.getTemplating().getTemplates();
        assertEquals("default", t.getName());
    }

    @SuppressWarnings("deprecation")
    @Test
    public final void testSetTemplates() {
        result.getTemplating().setTemplates(new BoomTemplate("gnuff", "text/plain", "ISO-8859-15"));
        @SuppressWarnings("unchecked")
        UserTemplate<Writer> t = result.getTemplating().getTemplates();
        assertEquals("gnuff", t.getName());
    }

    private static class TestRenderer extends Renderer {

        @Override
        public void render(Writer writer, Result result) throws IOException {
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public String getMimeType() {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public final void testUsesDefaultTemplate() {
        assertTrue(result.getTemplating().usesDefaultTemplate());
        result.getTemplating().setRenderer(new TestRenderer());
        assertFalse(result.getTemplating().usesDefaultTemplate());
    }

}
