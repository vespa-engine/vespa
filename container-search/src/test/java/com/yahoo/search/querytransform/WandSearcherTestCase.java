// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.DotProductItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WandItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Before;
import org.junit.Test;

import java.util.ListIterator;

import static com.yahoo.container.protect.Error.INVALID_QUERY_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testing of WandSearcher.
 */
public class WandSearcherTestCase {

    private static final String VESPA_FIELD = "vespa-field";
    private static final double delta = 0.0000001;

    private Execution exec;

    @SuppressWarnings("deprecation")
    private IndexFacts buildIndexFacts() {
        IndexFacts retval = new IndexFacts();
        retval.addIndex("test", new Index(VESPA_FIELD));
        retval.freeze();
        return retval;
    }

    private Execution buildExec() {
        return new Execution(new Chain<Searcher>(new WandSearcher()),
                Execution.Context.createContextStub(buildIndexFacts()));
    }

    private Query buildQuery(String wandFieldName, String wandTokens, String wandHeapSize, String wandType, String wandScoreThreshold, String wandThresholdBoostFactor) {
        Query q = new Query("");
        q.properties().set("wand.field", wandFieldName);
        q.properties().set("wand.tokens", wandTokens);
        if (wandHeapSize != null) {
            q.properties().set("wand.heapSize", wandHeapSize);
        }
        if (wandType != null) {
            q.properties().set("wand.type", wandType);
        }
        if (wandScoreThreshold != null) {
            q.properties().set("wand.scoreThreshold", wandScoreThreshold);
        }
        if (wandThresholdBoostFactor != null) {
            q.properties().set("wand.thresholdBoostFactor", wandThresholdBoostFactor);
        }
        q.setHits(9);
        return q;
    }

    private Query buildDefaultQuery(String wandFieldName, String wandHeapSize) {
        return buildQuery(wandFieldName, "{a:1,b:2,c:3}", wandHeapSize, null, null, null);
    }

    private Query buildDefaultQuery() {
        return buildQuery(VESPA_FIELD, "{a:1,\"b\":2,c:3}", null, null, null, null);
    }

    private void assertWordItem(String expToken, String expField, int expWeight, Item item) {
        WordItem wordItem = (WordItem) item;
        assertEquals(expToken, wordItem.getWord());
        assertEquals(expField, wordItem.getIndexName());
        assertEquals(expWeight, wordItem.getWeight());
    }

    @Before
    public void setUp() throws Exception {
        exec = buildExec();
    }

    @Test
    public void requireThatVespaWandCanBeSpecified() {
        Query q = buildDefaultQuery();
        Result r = exec.search(q);

        WeakAndItem root = (WeakAndItem)TestUtils.getQueryTreeRoot(r);
        assertEquals(100, root.getN());
        assertEquals(3, root.getItemCount());
        ListIterator<Item> itr = root.getItemIterator();
        assertWordItem("a", VESPA_FIELD, 1, itr.next());
        assertWordItem("b", VESPA_FIELD, 2, itr.next());
        assertWordItem("c", VESPA_FIELD, 3, itr.next());
        assertFalse(itr.hasNext());
    }

    @Test
    public void requireThatVespaWandHeapSizeCanBeSpecified() {
        Query q = buildDefaultQuery(VESPA_FIELD, "50");
        Result r = exec.search(q);

        WeakAndItem root = (WeakAndItem)TestUtils.getQueryTreeRoot(r);
        assertEquals(50, root.getN());
    }

    @Test
    public void requireThatWandCanBeSpecifiedTogetherWithNonAndQueryRoot() {
        Query q = buildDefaultQuery();
        q.getModel().getQueryTree().setRoot(new WordItem("foo", "otherfield"));
        Result r = exec.search(q);

        AndItem root = (AndItem)TestUtils.getQueryTreeRoot(r);
        assertEquals(2, root.getItemCount());
        ListIterator<Item> itr = root.getItemIterator();
        assertTrue(itr.next() instanceof WordItem);
        assertTrue(itr.next() instanceof WeakAndItem);
        assertFalse(itr.hasNext());
    }

    @Test
    public void requireThatWandCanBeSpecifiedTogetherWithAndQueryRoot() {
        Query q = buildDefaultQuery();
        {
            AndItem root = new AndItem();
            root.addItem(new WordItem("foo", "otherfield"));
            root.addItem(new WordItem("bar", "otherfield"));
            q.getModel().getQueryTree().setRoot(root);
        }
        Result r = exec.search(q);

        AndItem root = (AndItem)TestUtils.getQueryTreeRoot(r);
        assertEquals(3, root.getItemCount());
        ListIterator<Item> itr = root.getItemIterator();
        assertTrue(itr.next() instanceof WordItem);
        assertTrue(itr.next() instanceof WordItem);
        assertTrue(itr.next() instanceof WeakAndItem);
        assertFalse(itr.hasNext());
    }


    @Test
    public void requireThatNothingIsAddedWithoutWandPropertiesSet() {
        Query foo = new Query("");
        foo.getModel().getQueryTree().setRoot(new WordItem("foo", "otherfield"));
        Result r = exec.search(foo);

        WordItem root = (WordItem)TestUtils.getQueryTreeRoot(r);
        assertEquals("foo", root.getWord());
    }

    @Test
    public void requireThatErrorIsReturnedOnInvalidTokenList() {
        Query q = buildQuery(VESPA_FIELD, "{a1,b:1}", null, null, null, null);
        Result r = exec.search(q);

        ErrorMessage msg = r.hits().getError();
        assertNotNull(msg);
        assertEquals(INVALID_QUERY_PARAMETER.code, msg.getCode());
        assertEquals("'{a1,b:1}' is not a legal sparse vector string: Expected ':' starting at position 3 but was ','",msg.getDetailedMessage());
    }

    @Test
    public void requireThatErrorIsReturnedOnUnknownField() {
        Query q = buildDefaultQuery("unknown", "50");
        Result r = exec.search(q);
        ErrorMessage msg = r.hits().getError();
        assertNotNull(msg);
        assertEquals(INVALID_QUERY_PARAMETER.code, msg.getCode());
        assertEquals("Field 'unknown' was not found in index facts for search definitions [test]",msg.getDetailedMessage());
    }

    @Test
    public void requireThatVespaOrCanBeSpecified() {
        Query q = buildQuery(VESPA_FIELD, "{a:1,b:2,c:3}", null, "or", null, null);
        Result r = exec.search(q);

        OrItem root = (OrItem)TestUtils.getQueryTreeRoot(r);
        assertEquals(3, root.getItemCount());
        ListIterator<Item> itr = root.getItemIterator();
        assertWordItem("a", VESPA_FIELD, 1, itr.next());
        assertWordItem("b", VESPA_FIELD, 2, itr.next());
        assertWordItem("c", VESPA_FIELD, 3, itr.next());
        assertFalse(itr.hasNext());
    }

    private void assertWeightedSetItem(WeightedSetItem item) {
        assertEquals(3, item.getNumTokens());
        assertEquals(new Integer(1), item.getTokenWeight("a"));
        assertEquals(new Integer(2), item.getTokenWeight("b"));
        assertEquals(new Integer(3), item.getTokenWeight("c"));
    }

    @Test
    public void requireThatDefaultParallelWandCanBeSpecified() {
        Query q = buildQuery(VESPA_FIELD, "{a:1,b:2,c:3}", null, "parallel", null, null);
        Result r = exec.search(q);

        WandItem root = (WandItem)TestUtils.getQueryTreeRoot(r);
        assertEquals(VESPA_FIELD, root.getIndexName());
        assertEquals(100, root.getTargetNumHits());
        assertEquals(0.0, root.getScoreThreshold(), delta);
        assertEquals(1.0, root.getThresholdBoostFactor(), delta);
        assertWeightedSetItem(root);
    }

    @Test
    public void requireThatParallelWandCanBeSpecified() {
        Query q = buildQuery(VESPA_FIELD, "{a:1,b:2,c:3}", "50", "parallel", "70.5", "2.3");
        Result r = exec.search(q);

        WandItem root = (WandItem)TestUtils.getQueryTreeRoot(r);
        assertEquals(VESPA_FIELD, root.getIndexName());
        assertEquals(50, root.getTargetNumHits());
        assertEquals(70.5, root.getScoreThreshold(), delta);
        assertEquals(2.3, root.getThresholdBoostFactor(), delta);
        assertWeightedSetItem(root);
    }

    @Test
    public void requireThatDotProductCanBeSpecified() {
        Query q = buildQuery(VESPA_FIELD, "{a:1,b:2,c:3}", null, "dotProduct", null, null);
        Result r = exec.search(q);

        DotProductItem root = (DotProductItem)TestUtils.getQueryTreeRoot(r);
        assertEquals(VESPA_FIELD, root.getIndexName());
        assertWeightedSetItem(root);
    }

}
