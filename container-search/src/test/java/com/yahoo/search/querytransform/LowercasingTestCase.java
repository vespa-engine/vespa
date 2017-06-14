// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;


import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordAlternativesItem.Alternative;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * Tests term lowercasing in the search chain.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class LowercasingTestCase {

    private static final String TEDDY = "teddy";
    private static final String BAMSE = "bamse";
    IndexFacts settings;
    Execution execution;

    @Before
    public void setUp() throws Exception {
        IndexFacts f = new IndexFacts();
        Index bamse = new Index(BAMSE);
        Index teddy = new Index(TEDDY);
        Index defaultIndex = new Index("default");
        bamse.setLowercase(true);
        teddy.setLowercase(false);
        defaultIndex.setLowercase(true);
        f.addIndex("nalle", bamse);
        f.addIndex("nalle", teddy);
        f.addIndex("nalle", defaultIndex);
        f.freeze();
        settings = f;
        execution = new Execution(new Chain<Searcher>(
                new VespaLowercasingSearcher(new LowercasingConfig(new LowercasingConfig.Builder()))),
                Execution.Context.createContextStub(settings));
    }

    @After
    public void tearDown() throws Exception {
        execution = null;
    }

    @Test
    public void smoke() {
        Query q = new Query();
        AndItem root = new AndItem();
        WordItem tmp;
        tmp = new WordItem("Gnuff", BAMSE, true);
        root.addItem(tmp);
        tmp = new WordItem("Blaff", TEDDY, true);
        root.addItem(tmp);
        tmp = new WordItem("Blyant", "", true);
        root.addItem(tmp);
        q.getModel().getQueryTree().setRoot(root);

        Result r = execution.search(q);
        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WordItem w0 = (WordItem) root.getItem(0);
        WordItem w1 = (WordItem) root.getItem(1);
        WordItem w2 = (WordItem) root.getItem(2);
        assertEquals("gnuff", w0.getWord());
        assertEquals("Blaff", w1.getWord());
        assertEquals("blyant", w2.getWord());
    }

    @Test
    public void slightlyMoreComplexTree() {
        Query q = new Query();
        AndItem a0 = new AndItem();
        OrItem o0 = new OrItem();
        PhraseItem p0 = new PhraseItem();
        p0.setIndexName(BAMSE);
        PhraseSegmentItem p1 = new PhraseSegmentItem("Overbuljongterningpakkmesterassistent", true, false);
        p1.setIndexName(BAMSE);

        WordItem tmp;
        tmp = new WordItem("Nalle0", BAMSE, true);
        a0.addItem(tmp);

        tmp = new WordItem("Nalle1", BAMSE, true);
        o0.addItem(tmp);
        tmp = new WordItem("Nalle2", BAMSE, true);
        o0.addItem(tmp);
        a0.addItem(o0);

        tmp = new WordItem("Nalle3", BAMSE, true);
        p0.addItem(tmp);

        p1.addItem(new WordItem("Over", BAMSE, true));
        p1.addItem(new WordItem("buljong", BAMSE, true));
        p1.addItem(new WordItem("terning", BAMSE, true));
        p1.addItem(new WordItem("pakk", BAMSE, true));
        p1.addItem(new WordItem("Mester", BAMSE, true));
        p1.addItem(new WordItem("assistent", BAMSE, true));
        p1.lock();
        p0.addItem(p1);
        a0.addItem(p0);

        q.getModel().getQueryTree().setRoot(a0);

        Result r = execution.search(q);
        AndItem root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        tmp = (WordItem) root.getItem(0);
        assertEquals("nalle0", tmp.getWord());
        OrItem orElement = (OrItem) root.getItem(1);
        tmp = (WordItem) orElement.getItem(0);
        assertEquals("nalle1", tmp.getWord());
        tmp = (WordItem) orElement.getItem(1);
        assertEquals("nalle2", tmp.getWord());
        PhraseItem phrase = (PhraseItem) root.getItem(2);
        tmp = (WordItem) phrase.getItem(0);
        assertEquals("nalle3", tmp.getWord());
        PhraseSegmentItem locked = (PhraseSegmentItem) phrase.getItem(1);
        assertEquals("over", ((WordItem) locked.getItem(0)).getWord());
        assertEquals("buljong", ((WordItem) locked.getItem(1)).getWord());
        assertEquals("terning", ((WordItem) locked.getItem(2)).getWord());
        assertEquals("pakk", ((WordItem) locked.getItem(3)).getWord());
        assertEquals("mester", ((WordItem) locked.getItem(4)).getWord());
        assertEquals("assistent", ((WordItem) locked.getItem(5)).getWord());
    }

    @Test
    public void testWeightedSet() {
        Query q = new Query();
        AndItem root = new AndItem();
        WeightedSetItem tmp;
        tmp = new WeightedSetItem(BAMSE);
        tmp.addToken("AbC", 3);
        root.addItem(tmp);
        tmp = new WeightedSetItem(TEDDY);
        tmp.addToken("dEf", 5);
        root.addItem(tmp);
        q.getModel().getQueryTree().setRoot(root);
        Result r = execution.search(q);
        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WeightedSetItem w0 = (WeightedSetItem) root.getItem(0);
        WeightedSetItem w1 = (WeightedSetItem) root.getItem(1);
        assertEquals(1, w0.getNumTokens());
        assertEquals(1, w1.getNumTokens());
        assertEquals("abc", w0.getTokens().next().getKey());
        assertEquals("dEf", w1.getTokens().next().getKey());
    }

    @Test
    public void testDisableLowercasingWeightedSet() {
        execution = new Execution(new Chain<Searcher>(
                new VespaLowercasingSearcher(new LowercasingConfig(
                        new LowercasingConfig.Builder()
                                .transform_weighted_sets(false)))),
                Execution.Context.createContextStub(settings));

        Query q = new Query();
        AndItem root = new AndItem();
        WeightedSetItem tmp;
        tmp = new WeightedSetItem(BAMSE);
        tmp.addToken("AbC", 3);
        root.addItem(tmp);
        tmp = new WeightedSetItem(TEDDY);
        tmp.addToken("dEf", 5);
        root.addItem(tmp);
        q.getModel().getQueryTree().setRoot(root);
        Result r = execution.search(q);
        root = (AndItem) r.getQuery().getModel().getQueryTree().getRoot();
        WeightedSetItem w0 = (WeightedSetItem) root.getItem(0);
        WeightedSetItem w1 = (WeightedSetItem) root.getItem(1);
        assertEquals(1, w0.getNumTokens());
        assertEquals(1, w1.getNumTokens());
        assertEquals("AbC", w0.getTokens().next().getKey());
        assertEquals("dEf", w1.getTokens().next().getKey());
    }

    @Test
    public void testLowercasingWordAlternatives() {
        execution = new Execution(new Chain<Searcher>(new VespaLowercasingSearcher(new LowercasingConfig(
                new LowercasingConfig.Builder().transform_weighted_sets(false)))), Execution.Context.createContextStub(settings));

        Query q = new Query();
        WordAlternativesItem root;
        List<WordAlternativesItem.Alternative> terms = new ArrayList<>();
        terms.add(new Alternative("ABC", 1.0));
        terms.add(new Alternative("def", 1.0));
        root = new WordAlternativesItem(BAMSE, true, null, terms);
        q.getModel().getQueryTree().setRoot(root);
        Result r = execution.search(q);
        root = (WordAlternativesItem) r.getQuery().getModel().getQueryTree().getRoot();
        assertEquals(3, root.getAlternatives().size());
        assertEquals("ABC", root.getAlternatives().get(0).word);
        assertEquals(1.0d, root.getAlternatives().get(0).exactness, 1e-15d);
        assertEquals("abc", root.getAlternatives().get(1).word);
        assertEquals(.7d, root.getAlternatives().get(1).exactness, 1e-15d);
        assertEquals("def", root.getAlternatives().get(2).word);
        assertEquals(1.0d, root.getAlternatives().get(2).exactness, 1e-15d);
    }
}
