// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexFactsFactory;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.query.*;
import com.yahoo.prelude.querytransform.StemmingSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.searchchain.Execution;

import com.yahoo.search.test.QueryTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:mathiasm@yahoo-inc.com">Mathias M. Lidal</a>
 */
public class StemmingSearcherTestCase {

    private static final Linguistics linguistics = new SimpleLinguistics();
    private final IndexFacts indexFacts = IndexFactsFactory.newInstance("dir:src/test/java/com/yahoo/prelude/" +
                                                                        "querytransform/test/", null);

    @Test
    void testStemOnlySomeTerms() {
        assertStem("/search?query=Holes in CVS and Subversion nostem:Found",
                "WEAKAND(100) hole in cvs and subversion nostem:Found");
    }

    @Test
    void testPhraseSegmentTransforms() {
        Query q1 = buildQueryWithSegmentPhrase();
        executeStemming(q1);
        assertEquals("AND a 'd e'", q1.getModel().getQueryTree().getRoot().toString());
    }

    private Query buildQueryWithSegmentPhrase() {
        Query q1 = new Query("/search?query=placeholder&language=de");
        q1.getModel().setExecution(newExecution());
        AndItem root = new AndItem();
        root.addItem(new WordItem("a", true));
        // this is a trick, note the string to stem contains space
        PhraseSegmentItem p = new PhraseSegmentItem("d e", true, false);
        p.addItem(new WordItem("b", true));
        p.addItem(new WordItem("c", true));
        p.lock();
        root.addItem(p);
        q1.getModel().getQueryTree().setRoot(root);
        assertEquals("AND a 'b c'", q1.getModel().getQueryTree().getRoot().toString());
        return q1;
    }

    @Test
    void testPreserveConnectivityToPhrase() {
        Query q1 = buildQueryWithSegmentPhrase();
        CompositeItem r = (CompositeItem) q1.getModel().getQueryTree().getRoot();
        WordItem first = (WordItem) r.getItem(0);
        PhraseSegmentItem second = (PhraseSegmentItem) r.getItem(1);
        first.setConnectivity(second, 1.0d);
        executeStemming(q1);
        assertEquals("AND a 'd e'", q1.getModel().getQueryTree().getRoot().toString());
        r = (CompositeItem) q1.getModel().getQueryTree().getRoot();
        first = (WordItem) r.getItem(0);
        second = (PhraseSegmentItem) r.getItem(1);
        var origSecond = first.getConnectedItem();
        assertEquals(second, first.getConnectedItem(), "Connectivity incorrect.");
    }

    @Test
    void testDontStemPrefixes() {
        assertStem("/search?query=ist*&language=de", "WEAKAND(100) ist*");
    }

    @Test
    void testStemming() {
        Query query = new Query("/search?query=");
        executeStemming(query);
        assertTrue(query.getModel().getQueryTree().getRoot() instanceof NullItem);
    }

    @Test
    void testNounStemming() {
        assertStem("/search?query=noun:towers noun:tower noun:tow",
                "WEAKAND(100) noun:tower noun:tower noun:tow");
        assertStem("/search?query=notnoun:towers notnoun:tower notnoun:tow",
                "WEAKAND(100) notnoun:tower notnoun:tower notnoun:tow");
    }

    @SuppressWarnings("deprecation")
    @Test
    void testEmptyIndexInfo() {
        String indexInfoConfigID = "file:src/test/java/com/yahoo/prelude/querytransform/test/emptyindexinfo.cfg";
        ConfigGetter<IndexInfoConfig> getter = new ConfigGetter<>(IndexInfoConfig.class);
        IndexInfoConfig config = getter.getConfig(indexInfoConfigID);

        IndexFacts indexFacts = new IndexFacts(new IndexModel(config, (QrSearchersConfig) null));

        Query q = new Query(QueryTestCase.httpEncode("?query=cars"));
        new Execution(new Chain<Searcher>(new StemmingSearcher(linguistics)),
                Execution.Context.createContextStub(indexFacts, linguistics)).search(q);
        assertEquals("WEAKAND(100) cars", q.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testLiteralBoost() {
        Query q = new Query(QueryTestCase.httpEncode("/search?language=en&search=three"));
        WordItem scratch = new WordItem("trees", true);
        scratch.setStemmed(false);
        q.getModel().getQueryTree().setRoot(scratch);
        executeStemming(q);
        assertTrue(q.getModel().getQueryTree().getRoot() instanceof WordAlternativesItem,
                "Expected a set of word alternatives as root.");
        WordAlternativesItem w = (WordAlternativesItem) q.getModel().getQueryTree().getRoot();
        boolean foundExpectedBaseForm = false;
        for (WordAlternativesItem.Alternative a : w.getAlternatives()) {
            if ("trees".equals(a.word)) {
                assertEquals(1.0d, a.exactness, 1e-15);
                foundExpectedBaseForm = true;
            }
        }
        assertTrue(foundExpectedBaseForm, "Did not find original word form in query.");
    }

    @Test
    void testMultipleStemming() {
        Query q = new Query(QueryTestCase.httpEncode("/search?language=en&search=four&query=trees \"nouns girls\" flowers \"a verbs a\" girls&default-index=foobar"));
        executeStemming(q);
        assertEquals("WEAKAND(100) WORD_ALTERNATIVES foobar:[ tree(0.7) trees(1.0) ] " +
                "foobar:\"noun girl\" WORD_ALTERNATIVES foobar:[ flower(0.7) flowers(1.0) ] " +
                "foobar:\"a verb a\" WORD_ALTERNATIVES foobar:[ girl(0.7) girls(1.0) ]", q.getModel().getQueryTree().getRoot().toString());
    }

    private Execution.Context newExecutionContext() {
        return Execution.Context.createContextStub(indexFacts, linguistics);
    }

    private Execution newExecution() {
        return new Execution(newExecutionContext());
    }

    private void executeStemming(Query query) {
        new Execution(new Chain<Searcher>(new StemmingSearcher(linguistics)),
                      newExecutionContext()).search(query);
    }

    private void assertStem(String queryString, String expectedQueryTree) {
        assertStemEncoded(QueryTestCase.httpEncode(queryString), expectedQueryTree);
    }

    private void assertStemEncoded(String encodedQueryString, String expectedQueryTree) {
        Query query = new Query(encodedQueryString);
        executeStemming(query);
        assertEquals(expectedQueryTree, query.getModel().getQueryTree().getRoot().toString());
    }

}
