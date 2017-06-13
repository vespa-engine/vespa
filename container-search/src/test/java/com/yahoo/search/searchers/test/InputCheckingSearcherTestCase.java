// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.component.chain.Chain;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchers.InputCheckingSearcher;
import com.yahoo.text.Utf8;

/**
 * Functional test for InputCheckingSearcher.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class InputCheckingSearcherTestCase {

    Execution execution;

    @Before
    public void setUp() throws Exception {
        execution = new Execution(new Chain<Searcher>(new InputCheckingSearcher(MetricReceiver.nullImplementation)),
                Execution.Context.createContextStub(new IndexFacts()));
    }

    @After
    public void tearDown() throws Exception {
        execution = null;
    }

    @Test
    public final void testCommonCase() {
        Result r = execution.search(new Query("/search/?query=three+blind+mice"));
        assertNull(r.hits().getErrorHit());
    }

    @Test
    public final void candidateButAsciiOnly() {
        Result r = execution.search(new Query("/search/?query=a+a+a+a+a+a"));
        assertNull(r.hits().getErrorHit());
    }

    @Test
    public final void candidateButValid() throws UnsupportedEncodingException {
        Result r = execution.search(new Query("/search/?query=" + URLEncoder.encode("å å å å å å", "UTF-8")));
        assertNull(r.hits().getErrorHit());
    }

    @Test
    public final void candidateButValidAndOutsideFirst256() throws UnsupportedEncodingException {
        Result r = execution.search(new Query("/search/?query=" + URLEncoder.encode("œ œ œ œ œ œ", "UTF-8")));
        assertNull(r.hits().getErrorHit());
    }


    @Test
    public final void testDoubleEncoded() throws UnsupportedEncodingException {
        String rawQuery = "å å å å å å";
        byte[] encodedOnce = Utf8.toBytes(rawQuery);
        char[] secondEncodingBuffer = new char[encodedOnce.length];
        for (int i = 0; i < secondEncodingBuffer.length; ++i) {
            secondEncodingBuffer[i] = (char) (encodedOnce[i] & 0xFF);
        }
        String query = new String(secondEncodingBuffer);
        Result r = execution.search(new Query("/search/?query=" + URLEncoder.encode(query, "UTF-8")));
        assertEquals(1, r.hits().getErrorHit().errors().size());
    }

    @Test
    public final void testRepeatedConsecutiveTermsInPhrase() {
        Result r = execution.search(new Query("/search/?query=a.b.0.0.0.0.0.c"));
        assertNull(r.hits().getErrorHit());
        r = execution.search(new Query("/search/?query=a.b.0.0.0.0.0.0.c"));
        assertNotNull(r.hits().getErrorHit());
        r = execution.search(new Query("/search/?query=a.b.0.0.0.1.0.0.0.c"));
        assertNull(r.hits().getErrorHit());
    }
    @Test
    public final void testThatMaxRepeatedConsecutiveTermsInPhraseIs5() {
        Result r = execution.search(new Query("/search/?query=a.b.0.0.0.0.0.c"));
        assertNull(r.hits().getErrorHit());
        r = execution.search(new Query("/search/?query=a.b.0.0.0.0.0.0.c"));
        assertNotNull(r.hits().getErrorHit());
        r = execution.search(new Query("/search/?query=a.b.0.0.0.1.0.0.0.c"));
        assertNull(r.hits().getErrorHit());
    }
    @Test
    public final void testThatMaxRepeatedTermsInPhraseIs10() {
        Result r = execution.search(new Query("/search/?query=0.a.1.a.2.a.3.a.4.a.5.a.6.a.7.a.9.a"));
        assertNull(r.hits().getErrorHit());
        r = execution.search(new Query("/search/?query=0.a.1.a.2.a.3.a.4.a.5.a.6.a.7.a.8.a.9.a.10.a"));
        assertNotNull(r.hits().getErrorHit());
    }
}
