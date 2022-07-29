// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.*;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class QueryCanonicalizerTestCase {

    @Test
    void testSingleLevelSingleItemComposite() {
        CompositeItem root = new AndItem();

        root.addItem(new WordItem("word"));
        assertCanonicalized("word", null, root);
    }

    @Test
    void testSingleLevelSingleItemNonReducibleComposite() {
        CompositeItem root = new WeakAndItem();

        root.addItem(new WordItem("word"));
        assertCanonicalized("WEAKAND(100) word", null, root);
    }

    @Test
    void testMultilevelSingleItemComposite() {
        CompositeItem root = new AndItem();
        CompositeItem and1 = new AndItem();
        CompositeItem and2 = new AndItem();

        root.addItem(and1);
        and1.addItem(and2);
        and2.addItem(new WordItem("word"));
        assertCanonicalized("word", null, root);
    }

    @Test
    void testMultilevelComposite() {
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
    void testMultilevelEmptyComposite() {
        CompositeItem root = new AndItem();
        CompositeItem and1 = new AndItem();
        CompositeItem and2 = new AndItem();

        root.addItem(and1);
        and1.addItem(and2);
        assertCanonicalized(null, "No query", new Query());
    }

    @Test
    void testMultilevelMultiBranchEmptyComposite() {
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
    void testMultilevelMultiBranchSingleItemComposite() {
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
        assertCanonicalized("word", null, new Query("?query=word&type=all"));
    }

    @Test
    void testMultilevelWeakAndCollapsing() {
        CompositeItem root = new WeakAndItem();
        CompositeItem l1 = new WeakAndItem();
        CompositeItem l2 = new WeakAndItem();
        CompositeItem l3 = new WeakAndItem();
        CompositeItem l4 = new WeakAndItem();

        root.addItem(l1);

        l1.addItem(l2);
        l1.addItem(new WordItem("l1"));

        l2.addItem(l3);
        l2.addItem(new WordItem("l2"));

        l3.addItem(l4);
        l3.addItem(new WordItem("l3"));

        l4.addItem(new WordItem("l4"));

        assertCanonicalized("WEAKAND(100) l4 l3 l2 l1", null, root);
    }

    @Test
    void testWeakAndCollapsingRequireSameNAndIndex() {
        CompositeItem root = new WeakAndItem(10);
        CompositeItem l1 = new WeakAndItem(100);
        CompositeItem l2 = new WeakAndItem(100);
        l2.setIndexName("other");

        root.addItem(l1);

        l1.addItem(l2);
        l1.addItem(new WordItem("l1"));

        l2.addItem(new WordItem("l2"));

        assertCanonicalized("WEAKAND(10) (WEAKAND(100) (WEAKAND(100) l2) l1)", null, root);
    }

    @Test
    void testNullRoot() {
        assertCanonicalized(null, "No query", new Query());
    }

    @Test
    void testNestedNull() {
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
    void testNestedNullItem() {
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
    void testNestedNullAndSingle() {
        CompositeItem root = new AndItem();
        CompositeItem or = new OrItem();

        root.addItem(or);
        CompositeItem and = new AndItem();

        or.addItem(and);
        or.addItem(new WordItem("word"));
        assertCanonicalized("word", null, root);
    }

    @Test
    void testRemovalOfUnnecessaryComposites() {
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

    /** Multiple levels of the same AND/OR should collapse */
    @Test
    void testMultilevelCollapsing() {
        CompositeItem root = new AndItem();
        CompositeItem l1 = new AndItem();
        CompositeItem l2 = new AndItem();
        CompositeItem l3 = new AndItem();

        root.addItem(l1);

        l1.addItem(new WordItem("l1i1"));
        l1.addItem(l2);

        l2.addItem(new WordItem("l2i1"));
        l2.addItem(l3);
        l2.addItem(new WordItem("l2i2"));

        l3.addItem(new WordItem("l3i1"));
        l3.addItem(new WordItem("l3i2"));

        assertCanonicalized("AND l1i1 l2i1 l3i1 l3i2 l2i2", null, root);
    }

    /** Multiple levels of different composites should not collapse */
    @Test
    void testMultilevelNonCollapsing() {
        CompositeItem root = new AndItem();
        CompositeItem l1 = new AndItem();
        CompositeItem l2 = new OrItem();
        CompositeItem l3 = new AndItem();

        root.addItem(l1);

        l1.addItem(new WordItem("l1i1"));
        l1.addItem(l2);

        l2.addItem(new WordItem("l2i1"));
        l2.addItem(l3);

        l3.addItem(new WordItem("l3i1"));

        assertCanonicalized("AND l1i1 (OR l2i1 l3i1)", null, root);
    }

    /** Multiple levels of RANK should collapse */
    @Test
    void testMultilevelRankCollapsing() {
        CompositeItem root = new RankItem();
        CompositeItem l1 = new RankItem();
        CompositeItem l2 = new RankItem();
        CompositeItem l3 = new RankItem();
        CompositeItem l4 = new RankItem();

        root.addItem(l1);

        l1.addItem(l2);
        l1.addItem(new WordItem("l1"));

        l2.addItem(l3);
        l2.addItem(new WordItem("l2"));

        l3.addItem(l4);
        l3.addItem(new WordItem("l3"));

        l4.addItem(new WordItem("l4"));

        assertCanonicalized("RANK l4 l3 l2 l1", null, root);
    }

    @Test
    void testNegativeMustHaveNegatives() {
        CompositeItem root = new NotItem();

        root.addItem(new WordItem("positive"));
        assertCanonicalized("positive", null, root);
    }

    @Test
    void testNegative() {
        NotItem root = new NotItem();

        root.addNegativeItem(new WordItem("negative"));
        assertCanonicalized("-negative", null, root);
    }

    @Test
    void testNegativeOnly() {
        CompositeItem root = new AndItem();
        NotItem not = new NotItem();

        root.addItem(not);
        root.addItem(new WordItem("word"));
        not.addNegativeItem(new WordItem("negative"));
        assertCanonicalized("AND (-negative) word", null, root);
    }

    @Test
    void testCollapseFalseItemInAnd() {
        CompositeItem root = new AndItem();
        root.addItem(new WordItem("i1"));
        root.addItem(new FalseItem());
        assertCanonicalized("FALSE", null, root);
    }

    @Test
    void testRemoveFalseItemInOr() {
        CompositeItem root = new OrItem();
        AndItem and = new AndItem(); // this gets collapse to just FALSE, which is then removed
        root.addItem(and);
        and.addItem(new WordItem("i1"));
        and.addItem(new FalseItem());
        root.addItem(new WordItem("i1")); // ... which causes the OR to collapse, leaving this
        assertCanonicalized("i1", null, root);
    }

    @Test
    void testCollapseFalseItemInNot() {
        CompositeItem root = new NotItem();
        root.addItem(new FalseItem()); // false ANDNOT ... is false
        root.addItem(new WordItem("i1"));
        assertCanonicalized("FALSE", null, root);
    }

    @Test
    void testRemoveFalseItemInNot() {
        CompositeItem root = new NotItem();
        root.addItem(new WordItem("i1"));
        root.addItem(new FalseItem()); // ... ANDNOT false is redundant
        assertCanonicalized("i1", null, root);
    }

    @Test
    void testCollapseFalseItemInRank() {
        CompositeItem root = new RankItem();
        root.addItem(new FalseItem()); // false RANK ... is false
        root.addItem(new WordItem("i1"));
        assertCanonicalized("FALSE", null, root);
    }

    @Test
    void testRemoveFalseItemInRank() {
        CompositeItem root = new RankItem();
        root.addItem(new WordItem("i1"));
        root.addItem(new FalseItem()); // ... RANK false is redundant
        assertCanonicalized("i1", null, root);
    }

    /**
     * Tests that connexity is preserved by cloning and transferred to rank properties by preparing the query
     * (which strictly is an implementation detail which we should rather hide).
     */
    @Test
    void testConnexityAndCloning() {
        Query q = new Query("?query=a%20b");
        CompositeItem root = (CompositeItem) q.getModel().getQueryTree().getRoot();
        ((WordItem) root.getItem(0)).setConnectivity(root.getItem(1), java.lang.Math.E);
        q = q.clone();

        assertNull(q.getRanking().getProperties().get("vespa.term.1.connexity"), "Not prepared yet");
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
    void testSignificance() {
        Query q = new Query("?query=a%20b");
        CompositeItem root = (CompositeItem) q.getModel().getQueryTree().getRoot();
        ((WordItem) root.getItem(0)).setSignificance(0.5);
        ((WordItem) root.getItem(1)).setSignificance(0.95);
        q.prepare();
        assertEquals("0.5", q.getRanking().getProperties().get("vespa.term.1.significance").get(0));
        assertEquals("0.95", q.getRanking().getProperties().get("vespa.term.2.significance").get(0));
    }

    @Test
    void testPhraseWeight() {
        PhraseItem root = new PhraseItem();
        root.setWeight(200);
        root.addItem(new WordItem("a"));
        assertCanonicalized("a!200", null, root);
    }

    @Test
    void testEquivDuplicateRemoval() {
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
    void testRankDuplicateCheapification() {
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

    @Test
    void queryTreeExceedsAllowedSize() {
        Query query = new Query();
        QueryTree tree = query.getModel().getQueryTree();
        tree.setRoot(new WordItem("A"));
        tree.and(new WordItem("B"));

        assertNull(QueryCanonicalizer.canonicalize(query));
        query.properties().set("maxQueryItems", 2);
        assertEquals("Query tree exceeds allowed item count. Configured limit: 2 - Item count: 3", QueryCanonicalizer.canonicalize(query));
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
            assertNull(query.getModel().getQueryTree().getRoot());
        } else {
            assertEquals(canonicalForm, query.getModel().getQueryTree().getRoot().toString());
        }
    }

}
