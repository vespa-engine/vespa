// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser.test;

import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static org.junit.Assert.assertEquals;

/**
 * Check default index propagates correctly to the tokenizer.
 *
 * @author Steinar Knutsen
 */
public class ExactMatchAndDefaultIndexTestCase {

    @Test
    public void testExactMatchTokenization() {
        Index index = new Index("testexact");
        index.setExact(true, null);
        IndexFacts facts = new IndexFacts();
        facts.addIndex("testsd", index);
        Query q = new Query("?query=" + enc("a/b foo.com") + "&default-index=testexact");
        q.getModel().setExecution(new Execution(new Execution.Context(null, facts, null, null, null)));
        assertEquals("AND testexact:a/b testexact:foo.com", q.getModel().getQueryTree().getRoot().toString());
        q = new Query("?query=" + enc("a/b foo.com"));
        assertEquals("AND \"a b\" \"foo com\"", q.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testDefaultIndexSpecialChars() {
        Query q = new Query("?query=" + enc("dog & cat") + "&default-index=textsearch");
        assertEquals("AND textsearch:dog textsearch:cat", q.getModel().getQueryTree().getRoot().toString());
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
