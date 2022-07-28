// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser.test;

import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Check default index propagates correctly to the tokenizer.
 *
 * @author Steinar Knutsen
 */
public class ExactMatchAndDefaultIndexTestCase {

    @Test
    void testExactMatchTokenization() {
        SearchDefinition sd = new SearchDefinition("testsd");
        Index index = new Index("testexact");
        index.setExact(true, null);
        sd.addIndex(index);
        IndexFacts facts = new IndexFacts(new IndexModel(sd));

        Query q = new Query("?query=" + enc("a/b foo.com") + "&default-index=testexact");
        q.getModel().setExecution(new Execution(Execution.Context.createContextStub(facts)));
        assertEquals("WEAKAND(100) testexact:a/b testexact:foo.com", q.getModel().getQueryTree().getRoot().toString());
        q = new Query("?query=" + enc("a/b foo.com"));
        assertEquals("WEAKAND(100) (AND a b) (AND foo com)", q.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testDefaultIndexSpecialChars() {
        Query q = new Query("?query=" + enc("dog & cat") + "&default-index=textsearch");
        assertEquals("WEAKAND(100) textsearch:dog textsearch:cat", q.getModel().getQueryTree().getRoot().toString());
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

}
