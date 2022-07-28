// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.search.Query;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests semantic searching
 *
 * @author bratseth
 */
public class SemanticSearcherTestCase extends RuleBaseAbstractTestCase {

    public SemanticSearcherTestCase() {
        super("rules.sr");
    }

    @Test
    void testSingleShopping() {
        assertSemantics("brand:sony",
                "sony");
        assertSemantics("brand:sony!150",
                "sony!150");
    }

    @Test
    void testCombinedShopping() {
        assertSemantics("AND brand:sony category:camera",
                "sony camera");
    }

    @Test
    void testPhrasedShopping() {
        assertSemantics("AND brand:sony category:\"digital camera\"",
                "sony digital camera");
    }

    @Test
    void testSimpleLocal() {
        assertSemantics("AND listing:restaurant place:geary",
                "restaurant in geary");
    }

    @Test
    void testLocal() {
        assertSemantics("AND listing:restaurant place:\"geary street san francisco\"",
                "restaurant in geary street san francisco");
    }

    @Test
    void testLiteralReplacing() {
        assertSemantics("AND lord of rings", "lotr");
    }

    @Test
    void testAddingAnd() {
        assertSemantics("AND bar foobar:bar",
                "bar");
    }

    @Test
    void testAddingRank() {
        assertSemantics("RANK word foobar:word",
                "word");
    }

    @Test
    void testFilterIsIgnored() {
        assertSemantics("RANK word |a |word |b foobar:word",
                "word&filter=a word b");
        assertSemantics("RANK a |word |b",
                "a&filter=word b");
    }

    @Test
    void testAddingNegative() {
        assertSemantics("+java -coffee",
                "java");
    }

    @Test
    void testAddingNegativePluralToSingular() {
        assertSemantics("+javas -coffee",
                "javas");
    }

    @Test
    void testCombined() {
        assertSemantics("AND bar listing:restaurant place:\"geary street san francisco\" foobar:bar",
                "bar restaurant in geary street san francisco");
    }

    @Test
    void testStopWord() {
        assertSemantics("strokes", "the strokes");
    }

    @Test
    void testStopWords1() {
        assertSemantics("strokes", "be the strokes");
    }

    @Test
    void testStopWords2() {
        assertSemantics("strokes", "the strokes be");
    }

    @Test
    void testDontRemoveEverything() {
        assertSemantics("the", "the the the");
    }

    @Test
    void testMoreStopWordRemoval() {
        assertSemantics("hamlet", "hamlet to be or not to be");
    }

    @Test
    void testTypeChange() {
        assertSemantics("RANK default:typechange doors", "typechange doors");
    }

    @Test
    void testTypeChangeWithSingularToPluralButNonReplaceWillNotSingularify() {
        assertSemantics("RANK default:typechange door", "typechange door");
    }

    @Test
    void testExplicitContext() {
        assertSemantics("AND from:paris to:texas", "paris to texas");
    }

    @Test
    void testOrProduction() {
        assertSemantics("OR something somethingelse", "something");
    }

    // This test is order dependent. Fix it!!
    @Test
    void testWeightedSetItem() {
        Query q = new Query();
        WeightedSetItem weightedSet = new WeightedSetItem("fieldName");
        weightedSet.addToken("a", 1);
        weightedSet.addToken("b", 2);
        q.getModel().getQueryTree().setRoot(weightedSet);
        assertSemantics("WEIGHTEDSET fieldName{[1]:\"a\",[2]:\"b\"}", q);
    }

    @Test
    void testNullQuery() {
        Query query = new Query(""); // Causes a query containing a NullItem
        doSearch(searcher, query, 0, 10);
        assertEquals(NullItem.class, query.getModel().getQueryTree().getRoot().getClass()); // Still a NullItem
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        return new Execution(chainedAsSearchChain(searcher), Execution.Context.createContextStub());
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
