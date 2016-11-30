// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.*;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * @author bratseth
 */
public class QueryCanonicalizerTestCase {

    @Test
    public void testSingleLevelSingleItemComposite() {
        CompositeItem root = new AndItem();

        root.addItem(new WordItem("word"));
        assertCanonicalized("word", null, root);
    }

    @Test
    public void testSingleLevelSingleItemNonReducibleComposite() {
        CompositeItem root = new WeakAndItem();

        root.addItem(new WordItem("word"));
        assertCanonicalized("WAND(100) word", null, root);
    }

    @Test
    public void testMultilevelSingleItemComposite() {
        CompositeItem root = new AndItem();
        CompositeItem and1 = new AndItem();
        CompositeItem and2 = new AndItem();

        root.addItem(and1);
        and1.addItem(and2);
        and2.addItem(new WordItem("word"));
        assertCanonicalized("word", null, root);
    }

    @Test
    public void testMultilevelComposite() {
        // AND (RANK (AND a b c)) WAND(25,0.0,1.0)
        AndItem and = new AndItem();
        RankItem rank = new RankItem();
        and.addItem(rank);
        AndItem nestedAnd = new AndItem();
        nestedAnd.addItem(new WordItem("a"));
        nestedAnd.addItem(new WordItem("b"));
        nestedAnd.addItem(new WordItem("c"));
        rank.addItem(nestedAnd);
        WandItem wand = new WandItem("default", 100);
        and.addItem(wand);

        assertCanonicalized("AND a b c WAND(100,0.0,1.0) default}", null, and);
    }

    @Test
    public void testMultilevelEmptyComposite() {
        CompositeItem root = new AndItem();
        CompositeItem and1 = new AndItem();
        CompositeItem and2 = new AndItem();

        root.addItem(and1);
        and1.addItem(and2);
        assertCanonicalized(null, "No query", new Query());
    }

    @Test
    public void testMultilevelMultiBranchEmptyComposite() {
        CompositeItem root = new AndItem();
        CompositeItem and1 = new AndItem();
        CompositeItem and21 = new AndItem();
        CompositeItem and22 = new AndItem();
        CompositeItem and31 = new AndItem();
        CompositeItem and32 = new AndItem();

        root.addItem(and1);
        and1.addItem(and21);
        and1.addItem(and22);
        and22.addItem(and31);
        and22.addItem(and32);
        assertCanonicalized(null, "No query", new Query());
    }

    @Test
    public void testMultilevelMultiBranchSingleItemComposite() {
        CompositeItem root = new AndItem();
        CompositeItem and1 = new AndItem();
        CompositeItem and21 = new AndItem();
        CompositeItem and22 = new AndItem();
        CompositeItem and31 = new AndItem();
        CompositeItem and32 = new AndItem();

        root.addItem(and1);
        and1.addItem(and21);
        and1.addItem(and22);
        and22.addItem(and31);
        and22.addItem(and32);
        and22.addItem(new WordItem("word"));
        assertCanonicalized("word", null, new Query("?query=word"));
    }

    @Test
    public void testNullRoot() {
        assertCanonicalized(null, "No query", new Query());
    }

    @Test
    public void testNestedNull() {
        CompositeItem root = new AndItem();
        CompositeItem or = new AndItem();
        CompositeItem and = new AndItem();

        root.addItem(or);
        or.addItem(and);
        Query query = new Query();

        query.getModel().getQueryTree().setRoot(root);

        assertCanonicalized(null, "No query", root);
    }

    @Test
    public void testNestedNullItem() {
        CompositeItem root = new AndItem();
        CompositeItem or = new AndItem();
        CompositeItem and = new AndItem();
        and.addItem(new NullItem());
        and.addItem(new NullItem());

        root.addItem(or);
        or.addItem(and);
        Query query = new Query();

        query.getModel().getQueryTree().setRoot(root);

        assertCanonicalized(null, "No query", root);
    }

    @Test
    public void testNestedNullAndSingle() {
        CompositeItem root = new AndItem();
        CompositeItem or = new OrItem();

        root.addItem(or);
        CompositeItem and = new AndItem();

        or.addItem(and);
        or.addItem(new WordItem("word"));
        assertCanonicalized("word", null, root);
    }

    @Test
    public void testRemovalOfUnnecessaryComposites() {
        CompositeItem root = new AndItem();
        CompositeItem or = new OrItem();

        root.addItem(or);
        CompositeItem and = new AndItem();

        or.addItem(new WordItem("word1"));
        or.addItem(and);
        or.addItem(new WordItem("word2"));
        or.addItem(new WordItem("word3"));
        assertCanonicalized("OR word1 word2 word3", null, root);
    }

    /** Multiple levels of the same composite should collapse */
    @Test
    public void testMultilevelCollapsing() {
        CompositeItem root = new AndItem();
        CompositeItem child = new AndItem();
        CompositeItem grandchild = new AndItem();
        CompositeItem grandgrandchild = new AndItem();
        
        root.addItem(child);
        child.addItem(new WordItem("childItem"));

        child.addItem(grandchild);
        grandchild.addItem(new WordItem("grandchildItem"));

        grandchild.addItem(grandgrandchild);
        grandgrandchild.addItem(new WordItem("grandgrandchildItem"));

        assertCanonicalized("AND childItem grandchildItem grandgrandchildItem", null, root);
    }

    @Test
    public void testNegativeMustHaveNegatives() {
        CompositeItem root = new NotItem();

        root.addItem(new WordItem("positive"));
        assertCanonicalized("positive", null, root);
    }

    @Test
    public void testNegativeMustHavePositive() {
        NotItem root = new NotItem();

        root.addNegativeItem(new WordItem("negative"));
        assertCanonicalized("+(null) -negative","Can not search for only negative items", root);
    }

    @Test
    public void testNegativeMustHavePositiveNested() {
        CompositeItem root = new AndItem();
        NotItem not = new NotItem();

        root.addItem(not);
        root.addItem(new WordItem("word"));
        not.addNegativeItem(new WordItem("negative"));
        assertCanonicalized("AND (+(null) -negative) word","Can not search for only negative items", root);
    }

    /**
     * Tests that connexity is preserved by cloning and transferred to rank properties by preparing the query
     * (which strictly is an implementation detail which we should rather hide).
     */
    @Test
    public void testConnexityAndCloning() {
        Query q = new Query("?query=a%20b");
        CompositeItem root = (CompositeItem) q.getModel().getQueryTree().getRoot();
        ((WordItem) root.getItem(0)).setConnectivity(root.getItem(1), java.lang.Math.E);
        q = q.clone();

        assertNull("Not prepared yet", q.getRanking().getProperties().get("vespa.term.1.connexity"));
        q.prepare();
        assertEquals("2", q.getRanking().getProperties().get("vespa.term.1.connexity").get(0));
        assertEquals("2.718281828459045", q.getRanking().getProperties().get("vespa.term.1.connexity").get(1));
        q = q.clone(); // The clone stays prepared
        assertEquals("2", q.getRanking().getProperties().get("vespa.term.1.connexity").get(0));
        assertEquals("2.718281828459045", q.getRanking().getProperties().get("vespa.term.1.connexity").get(1));
    }

    /**
     * Tests that significance is transferred to rank properties by preparing the query
     * (which strictly is an implementation detail which we should rather hide).
     */
    @Test
    public void testSignificance() {
        Query q = new Query("?query=a%20b");
        CompositeItem root = (CompositeItem) q.getModel().getQueryTree().getRoot();
        ((WordItem) root.getItem(0)).setSignificance(0.5);
        ((WordItem) root.getItem(1)).setSignificance(0.95);
        q.prepare();
        assertEquals("0.5", q.getRanking().getProperties().get("vespa.term.1.significance").get(0));
        assertEquals("0.95", q.getRanking().getProperties().get("vespa.term.2.significance").get(0));
    }

    @Test
    public void testPhraseWeight() {
        PhraseItem root = new PhraseItem();
        root.setWeight(200);
        root.addItem(new WordItem("a"));
        assertCanonicalized("a!200", null, root);
    }

    @Test
    public void testEquivDuplicateRemoval() {
        {
            EquivItem root = new EquivItem();
            root.addItem(new WordItem("a"));
            root.addItem(new WordItem("b"));
            assertCanonicalized("EQUIV a b", null, root);
        }
        {
            EquivItem root = new EquivItem();
            root.addItem(new WordItem("a"));
            root.addItem(new WordItem("b"));
            assertCanonicalized("EQUIV a b", null, root);
        }
        {
            EquivItem root = new EquivItem();
            root.addItem(new WordItem("a"));
            root.addItem(new WordItem("a"));
            assertCanonicalized("a", null, root);
        }
        {
            EquivItem root = new EquivItem();
            root.addItem(new WordItem("a"));
            root.addItem(new WordItem("a"));
            root.addItem(new WordItem("a"));
            root.addItem(new WordItem("a"));
            root.addItem(new WordItem("a"));
            assertCanonicalized("a", null, root);
        }
        {
            EquivItem root = new EquivItem();
            root.addItem(new WordItem("a"));
            root.addItem(new WordItem("b"));
            root.addItem(new WordItem("a"));
            assertCanonicalized("EQUIV a b", null, root);
        }
        {
            EquivItem root = new EquivItem();
            PhraseItem one = new PhraseItem();
            PhraseItem theOther = new PhraseItem();
            WordItem first = new WordItem("a");
            WordItem second = new WordItem("b");
            one.addItem(first);
            one.addItem(second);
            theOther.addItem(first.clone());
            theOther.addItem(second.clone());
            root.addItem(one);
            root.addItem(theOther);
            assertCanonicalized("\"a b\"", null, root);
        }
        {
            EquivItem root = new EquivItem();
            PhraseSegmentItem one = new PhraseSegmentItem("a b", "a b", true, false);
            PhraseSegmentItem theOther = new PhraseSegmentItem("a b", "a b", true, false);
            WordItem first = new WordItem("a");
            WordItem second = new WordItem("b");
            one.addItem(first);
            one.addItem(second);
            theOther.addItem(first.clone());
            theOther.addItem(second.clone());
            root.addItem(one);
            root.addItem(theOther);
            assertCanonicalized("'a b'", null, root);
        }
    }

    @Test
    public void testRankDuplicateCheapification() {
        AndItem and = new AndItem();
        WordItem shoe = new WordItem("shoe", "prod");
        and.addItem(shoe);
        and.addItem(new WordItem("apparel & accessories", "tcnm"));
        RankItem rank = new RankItem();
        rank.addItem(and);

        rank.addItem(new WordItem("shoe", "prod")); // rank item which also ossurs in first argument
        for (int i = 0; i < 25; i++)
            rank.addItem(new WordItem("word" + i, "normbrnd"));
        QueryTree tree = new QueryTree(rank);

        assertTrue(shoe.isRanked());
        assertTrue(shoe.usePositionData());
        QueryCanonicalizer.canonicalize(tree);
        assertFalse(shoe.isRanked());
        assertFalse(shoe.usePositionData());
    }

    private void assertCanonicalized(String canonicalForm, String expectedError, Item root) {
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(root);
        assertCanonicalized(canonicalForm, expectedError, query);
    }

    private void assertCanonicalized(String canonicalForm, String expectedError, Query query) {
        String error = QueryCanonicalizer.canonicalize(query);

        assertEquals(expectedError, error);
        if (canonicalForm == null) {
            assertNull(null, query.getModel().getQueryTree().getRoot());
        } else {
            assertEquals(canonicalForm, query.getModel().getQueryTree().getRoot().toString());
        }
    }

}
