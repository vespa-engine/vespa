// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.search.Query;
import com.yahoo.prelude.querytransform.IndexCombinatorSearcher;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Control query transformations when doing index name expansion in QRS.
 *
 * @author Steinar Knutsen
 */
public class IndexCombinatorTestCase {

    private Searcher transformer;
    private IndexFacts f;

    @Before
    public void setUp() throws Exception {
        transformer = new IndexCombinatorSearcher();
        f = new IndexFacts();
        f.addIndex("one", "z");
        Index i = new Index("default");
        i.addCommand("match-group a i");
        f.addIndex("one", i);
    }

    @Test
    public void testDoNothing() {
        Result r = search("?query=z:y");
        assertEquals("z:y", r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    private Result search(String query) {
        return new Execution(transformer, Execution.Context.createContextStub(f)).search(new Query(QueryTestCase.httpEncode(query)));
    }

    @Test
    public void testBasic() {
        Result r = search("?query=y");
        assertEquals("OR a:y i:y", r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testBasicPair() {
        Result r = search("?query=x y");
        assertEquals(
                "OR (AND a:x a:y) (AND a:x i:y) (AND i:x a:y) (AND i:x i:y)", r
                        .getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testBasicTriplet() {
        Result r = search("?query=x y z");
        assertEquals("AND (OR a:x i:x) (OR a:y i:y) (OR a:z i:z)", r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testBasicMixedSinglet() {
        Result r = search("?query=x z:q");
        assertEquals("OR (AND a:x z:q) (AND i:x z:q)", r.getQuery().getModel().getQueryTree().getRoot()
                .toString());
    }

    @Test
    public void testBasicMixedPair() {
        Result r = search("?query=x y z:q");
        assertEquals(
                "OR (AND a:x a:y z:q) (AND a:x i:y z:q) (AND i:x a:y z:q) (AND i:x i:y z:q)",
                r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testBasicMixedTriplet() {
        Result r = search("?query=x y z:q r");
        assertEquals("AND (OR a:x i:x) (OR a:y i:y) z:q (OR a:r i:r)", r
                .getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testBasicOr() {
        Result r = search("?query=x y&type=any");
        assertEquals("OR a:y i:y a:x i:x", r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testBasicPhrase() {
        Result r = search("?query=\"x y\"");
        assertEquals("OR a:x y i:x y", r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testPhraseAndTerm() {
        Result r = search("?query=\"x y\" z");
        assertEquals(
                "OR (AND a:x y a:z) (AND a:x y i:z) (AND i:x y a:z) (AND i:x y i:z)",
                r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testBasicNot() {
        Result r = search("?query=+x -y");
        assertEquals("+(OR a:x i:x) -(OR a:y i:y)", r.getQuery().getModel().getQueryTree().getRoot()
                .toString());
    }

    @Test
    public void testLessBasicNot() {
        Result r = search("?query=a and b andnot c&type=adv");
        assertEquals(
                "+(OR (AND a:a a:b) (AND a:a i:b) (AND i:a a:b) (AND i:a i:b)) -(OR a:c i:c)",
                r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testLongerAndInPositive() {
        Result r = search("?query=a and b and c andnot d&type=adv");
        assertEquals(
                "+(AND (OR a:a i:a) (OR a:b i:b) (OR a:c i:c)) -(OR a:d i:d)", r
                        .getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testTreeInNegativeBranch() {
        Result r = search("?query=a andnot (b and c)&type=adv");
        assertEquals("+(OR a:a i:a) -(AND (OR a:b i:b) (OR a:c i:c))", r
                .getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testSomeTerms() {
        Result r = search("?query=a b -c +d g.h \"abc def\" z:q");
        assertEquals(
                "+(AND (OR a:a i:a) (OR a:b i:b) (OR a:d i:d) (OR a:g h i:g h) (OR a:abc def i:abc def) z:q) -(OR a:c i:c)",
                r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

    @Test
    public void testMixedIndicesAndAttributes() {
        String indexInfoConfigID = "file:src/test/java/com/yahoo/prelude/querytransform/test/indexcombinator.cfg";
        ConfigGetter<IndexInfoConfig> getter = new ConfigGetter<>(IndexInfoConfig.class);
        IndexInfoConfig config = getter.getConfig(indexInfoConfigID);
        IndexFacts facts = new IndexFacts(new IndexModel(config, (QrSearchersConfig)null));

        Result r = new Execution(transformer, Execution.Context.createContextStub(facts)).search(new Query(QueryTestCase.httpEncode("?query=\"a b\"")));
        assertEquals("OR default:\"a b\" attribute1:a b attribute2:a b", r
                .getQuery().getModel().getQueryTree().getRoot().toString());
        r = new Execution(transformer, Execution.Context.createContextStub(facts)).search(new Query(QueryTestCase.httpEncode("?query=\"a b\" \"c d\"")));
        assertEquals(
                "OR (AND default:\"a b\" default:\"c d\")"
                + " (AND default:\"a b\" attribute1:c d)"
                + " (AND default:\"a b\" attribute2:c d)"
                + " (AND attribute1:a b default:\"c d\")"
                + " (AND attribute1:a b attribute1:c d)"
                + " (AND attribute1:a b attribute2:c d)"
                + " (AND attribute2:a b default:\"c d\")"
                + " (AND attribute2:a b attribute1:c d)"
                + " (AND attribute2:a b attribute2:c d)",
                r.getQuery().getModel().getQueryTree().getRoot().toString());
    }

}
