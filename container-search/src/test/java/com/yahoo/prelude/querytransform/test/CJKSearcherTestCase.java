// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexFactsFactory;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.query.parser.TestLinguistics;
import com.yahoo.prelude.querytransform.CJKSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.querytransform.NGramSearcher;
import com.yahoo.search.searchchain.Execution;

import com.yahoo.search.test.QueryTestCase;
import com.yahoo.search.yql.MinimalQueryInserter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Steinar Knutsen
 */
public class CJKSearcherTestCase {

    private final IndexFacts indexFacts = IndexFactsFactory.newInstance("file:src/test/java/com/yahoo/prelude/" +
                                                                        "querytransform/test/cjk-index-info.cfg");

    @Test
    void testTermWeight() {
        assertTransformed("efg!10", "SAND e!10 fg!10",
                Query.Type.ALL, Language.CHINESE_SIMPLIFIED, Language.CHINESE_TRADITIONAL, TestLinguistics.INSTANCE);
    }

    /**
     * Overlapping tokens splits some sequences of "bcd" into "bc" "cd" instead of e.g. "b",
     * "cd". This improves recall in some cases. Vespa
     * must combine overlapping tokens as PHRASE, not AND to avoid a too high recall because of the token overlap.
     */
    @Test
    void testCjkQueryWithOverlappingTokens() {
        // The test language segmenter will segment "bcd" into the overlapping tokens "bc" "cd"
        assertTransformed("bcd", "SAND bc cd", Query.Type.ALL,
                          Language.CHINESE_SIMPLIFIED, Language.CHINESE_TRADITIONAL, TestLinguistics.INSTANCE);

        // While "efg" will be segmented into one of the standard options, "e" "fg"
        assertTransformed("efg", "SAND e fg", Query.Type.ALL,
                          Language.CHINESE_SIMPLIFIED, Language.CHINESE_TRADITIONAL, TestLinguistics.INSTANCE);
    }

    /**
     * The NGram searcher (in phrase mode) will create overlapping tokens in a regular Phrase, not SAND.
     * These should also not be rewritten here.
     */
    @Test
    void testCjkQueryWithPhraseComingFromNGrams() {
        assertTransformed("gram:123", "gram:\"12 23\"", Query.Type.ALL,
                          Language.CHINESE_SIMPLIFIED, Language.CHINESE_TRADITIONAL, TestLinguistics.INSTANCE);
    }

    @Test
    public void testEquivAndChinese() {
        Query query = new Query(QueryTestCase.httpEncode("search?yql=select * from music-only where default contains equiv('a', 'b c') or default contains '东'"));
        new Execution(new Chain<>(new MinimalQueryInserter(), new CJKSearcher()), Execution.Context.createContextStub()).search(query);
        assertEquals("OR (EQUIV default:a default:'b c') default:东", query.getModel().getQueryTree().toString());
    }

    /** Constructs a PhraseSegmentItem simulating CJK segmentation of "efg" into "e" + "fg" */
    private PhraseSegmentItem createSegmentedItem(Language language) {
        PhraseSegmentItem segment = new PhraseSegmentItem("efg", "efg", true, false);
        segment.addItem(new WordItem("e", "default"));
        segment.addItem(new WordItem("fg", "default"));
        segment.setLanguage(language);
        return segment;
    }

    @Test
    void testCjkTransformAppliesOnlyToCjkBranch() {
        // CJK branch: segmented phrase, marked Chinese
        PhraseSegmentItem cjkBranch = createSegmentedItem(Language.CHINESE_SIMPLIFIED);

        // English branch: simple word
        WordItem englishBranch = new WordItem("hello", "default");
        englishBranch.setLanguage(Language.ENGLISH);

        OrItem root = new OrItem();
        root.addItem(cjkBranch);
        root.addItem(englishBranch);

        // Query language is English — CJKSearcher should segment the CJK branch
        Query query = new Query("?language=en");
        query.getModel().getQueryTree().setRoot(root);

        new Execution(new Chain<Searcher>(new CJKSearcher()),
                      Execution.Context.createContextStub(indexFacts, TestLinguistics.INSTANCE)).search(query);

        String result = query.getModel().getQueryTree().getRoot().toString();
        assertTrue(result.contains("SAND"), "CJK branch should be transformed to SAND: " + result);
        assertTrue(result.contains("hello"), "English branch should be untouched: " + result);
    }

    @Test
    void testNonCjkBranchNotTransformedWhenQueryLanguageIsCjk() {
        // CJK branch: segmented phrase, marked Chinese
        PhraseSegmentItem cjkBranch = createSegmentedItem(Language.CHINESE_SIMPLIFIED);

        // English branch: same segmented structure, but marked English
        PhraseSegmentItem englishBranch = createSegmentedItem(Language.ENGLISH);
        String enBefore = englishBranch.toString();

        OrItem root = new OrItem();
        root.addItem(cjkBranch);
        root.addItem(englishBranch);

        // Query language is CJK — CJKSearcher should transform the CJK branch and leave the English branch untouched
        Query query = new Query("?language=zh-hans");
        query.getModel().getQueryTree().setRoot(root);

        new Execution(new Chain<Searcher>(new CJKSearcher()),
                      Execution.Context.createContextStub(indexFacts, TestLinguistics.INSTANCE)).search(query);

        Item resultRoot = query.getModel().getQueryTree().getRoot();
        assertTrue(resultRoot instanceof OrItem, "Root should be OR: " + resultRoot);
        OrItem or = (OrItem) resultRoot;
        assertEquals(2, or.getItemCount());

        // CJK branch: should be transformed
        assertTrue(or.getItem(0).toString().contains("SAND"),
                "CJK branch should be transformed: " + or.getItem(0));

        // English branch: should NOT be transformed
        assertEquals(enBefore, or.getItem(1).toString(),
                "English branch should NOT be transformed");
    }

    @Test
    void testTraceFiresWhenCjkItemInsideCompositeIsTransformed() {
        PhraseSegmentItem cjkBranch = createSegmentedItem(Language.CHINESE_SIMPLIFIED);
        WordItem englishBranch = new WordItem("hello", "default");
        englishBranch.setLanguage(Language.ENGLISH);

        OrItem root = new OrItem();
        root.addItem(cjkBranch);
        root.addItem(englishBranch);

        Query query = new Query("?language=en&tracelevel=2");
        query.getModel().getQueryTree().setRoot(root);

        new Execution(new Chain<Searcher>(new CJKSearcher()),
                      Execution.Context.createContextStub(indexFacts, TestLinguistics.INSTANCE)).search(query);

        String trace = query.getContext(false).getTrace().toString();
        assertTrue(trace.contains("Rewriting for CJK behavior for implicit phrases"),
                "Trace should contain CJK rewriting message: " + trace);
    }

    @Test
    void testNoTraceWhenNoCjkTransformationNeeded() {
        WordItem branch1 = new WordItem("hello", "default");
        branch1.setLanguage(Language.ENGLISH);
        WordItem branch2 = new WordItem("world", "default");
        branch2.setLanguage(Language.FRENCH);

        OrItem root = new OrItem();
        root.addItem(branch1);
        root.addItem(branch2);

        Query query = new Query("?language=en&tracelevel=2");
        query.getModel().getQueryTree().setRoot(root);

        new Execution(new Chain<Searcher>(new CJKSearcher()),
                      Execution.Context.createContextStub(indexFacts, TestLinguistics.INSTANCE)).search(query);

        String trace = query.getContext(false).getTrace().toString();
        assertFalse(trace.contains("Rewriting for CJK behavior for implicit phrases"),
                "Trace should NOT contain CJK rewriting message when no CJK items: " + trace);
    }

    private void assertTransformed(String queryString, String expected, Query.Type mode, Language actualLanguage,
                                   Language queryLanguage, Linguistics linguistics) {
        Parser parser = ParserFactory.newInstance(mode, new ParserEnvironment()
                .setIndexFacts(indexFacts)
                .setLinguistics(linguistics));
        Item root = parser.parse(new Parsable().setQuery(queryString).setLanguage(actualLanguage)).getRoot();
        assertFalse(root instanceof NullItem);

        Query query = new Query("?language=" + queryLanguage.languageCode() + "&gram.match=phrase");
        query.getModel().getQueryTree().setRoot(root);

        new Execution(new Chain<>(new NGramSearcher(linguistics), new CJKSearcher()),
                                  Execution.Context.createContextStub(indexFacts, linguistics)).search(query);
        assertEquals(expected, query.getModel().getQueryTree().getRoot().toString());
    }

}
