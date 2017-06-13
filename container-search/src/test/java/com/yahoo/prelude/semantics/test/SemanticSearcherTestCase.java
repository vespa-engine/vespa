// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.chain.Chain;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.search.Query;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.searchchain.Execution;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests semantic searching
 *
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class SemanticSearcherTestCase extends RuleBaseAbstractTestCase {

    public SemanticSearcherTestCase(String name) {
        super(name,"rules.sr");
    }

    public void testSingleShopping() {
        assertSemantics("brand:sony",
                               "sony");
    }

    public void testCombinedShopping() {
        assertSemantics("AND brand:sony category:camera",
                        "sony camera");
    }

    public void testPhrasedShopping() {
        assertSemantics("AND brand:sony category:\"digital camera\"",
                        "sony digital camera");
    }

    public void testSimpleLocal() {
        assertSemantics("AND listing:restaurant place:geary",
                        "restaurant in geary");
    }

    public void testLocal() {
        assertSemantics("AND listing:restaurant place:\"geary street san francisco\"",
                        "restaurant in geary street san francisco");
    }

    public void testLiteralReplacing() {
        assertSemantics("AND lord of rings","lotr");
    }

    public void testAddingAnd() {
        assertSemantics("AND bar foobar:bar",
                        "bar");
    }

    public void testAddingRank() {
        assertSemantics("RANK word foobar:word",
                        "word");
    }

    public void testFilterIsIgnored() {
        assertSemantics("RANK word |a |word |b foobar:word",
                        "word&filter=a word b");
        assertSemantics("RANK a |word |b",
                        "a&filter=word b");
    }

    public void testAddingNegative() {
        assertSemantics("+java -coffee",
                        "java");
    }

    public void testAddingNegativePluralToSingular() {
        assertSemantics("+javas -coffee",
                        "javas");
    }

    public void testCombined() {
        assertSemantics("AND bar listing:restaurant place:\"geary street san francisco\" foobar:bar",
                        "bar restaurant in geary street san francisco");
    }

    public void testStopWord() {
        assertSemantics("strokes","the strokes");
    }

    public void testStopWords1() {
        assertSemantics("strokes","be the strokes");
    }

    public void testStopWords2() {
        assertSemantics("strokes","the strokes be");
    }

    public void testDontRemoveEverything() {
        assertSemantics("the","the the the");
    }

    public void testMoreStopWordRemoval() {
        assertSemantics("hamlet","hamlet to be or not to be");
    }

    public void testTypeChange() {
        assertSemantics("RANK doors default:typechange","typechange doors");
    }

    public void testTypeChangeWithSingularToPluralButNonReplaceWillNotSingularify() {
        assertSemantics("RANK door default:typechange","typechange door");
    }

    public void testExplicitContext() {
        assertSemantics("AND from:paris to:texas","paris to texas");
    }

    public void testPluralReplaceBecomesSingular() {
        assertSemantics("AND from:paris to:texas","pariss to texass");
    }

    public void testOrProduction() {
        assertSemantics("OR something somethingelse","something");
    }

    //This test is order dependent. Fix it!!
    public void testWeightedSetItem() {
        Query q = new Query();
        WeightedSetItem weightedSet=new WeightedSetItem("fieldName");
        weightedSet.addToken("a",1);
        weightedSet.addToken("b",2);
        q.getModel().getQueryTree().setRoot(weightedSet);
        assertSemantics("WEIGHTEDSET fieldName{[1]:\"a\",[2]:\"b\"}",q);
    }

    public void testNullQuery() {
        Query query=new Query(""); // Causes a query containing a NullItem
        doSearch(searcher, query, 0, 10);
        assertEquals(NullItem.class, query.getModel().getQueryTree().getRoot().getClass()); // Still a NullItem
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher).search(query);
    }

    private Execution createExecution(Searcher searcher) {
        Execution.Context context = new Execution.Context(null, null, null, new RendererRegistry(MoreExecutors.directExecutor()), new SimpleLinguistics());
        return new Execution(chainedAsSearchChain(searcher), context);
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain) {
        List<Searcher> searchers = new ArrayList<>();
        searchers.add(topOfChain);
        return new Chain<>(searchers);
    }

}
