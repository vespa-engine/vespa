// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.TrueItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.jupiter.api.Test;

import static com.yahoo.search.searchers.OpportunisticWeakAndSearcher.targetHits;
import static com.yahoo.search.searchers.OpportunisticWeakAndSearcher.weakAnd2AndRecurse;
import static com.yahoo.search.searchers.OpportunisticWeakAndSearcher.adjustWeakAndHeap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;


public class OpportunisticWeakAndSearcherTestCase {
    private static Item buildQueryItem(CompositeItem root, CompositeItem injectAtLevel2) {
        root.addItem(new WordItem("text"));
        injectAtLevel2.addItem(new WordItem("a"));
        injectAtLevel2.addItem(new WordItem("b"));
        root.addItem(injectAtLevel2);
        return root;
    }

    private static CompositeItem addItem(CompositeItem composite, Item item) {
        composite.addItem(item);
        return composite;
    }

    @Test
    public void requireThatWeakAndIsDetected() {
        assertEquals(-1, targetHits(new OrItem()));
        assertEquals(-1, targetHits(new WeakAndItem(33)));
        assertEquals(-1, targetHits(addItem(new WeakAndItem(33), new TrueItem())));
        assertEquals(33, targetHits(addItem(addItem(new WeakAndItem(33), new TrueItem()), new TrueItem())));
        assertEquals(77, targetHits(buildQueryItem(new OrItem(), new WeakAndItem(77))));
        assertEquals(77, targetHits(buildQueryItem(new AndItem(), new WeakAndItem(77))));
        assertEquals(-1, targetHits(buildQueryItem(new OrItem(), new AndItem())));
    }

    @Test
    public void requireThatWeakAndIsReplacedWithAnd() {
        assertEquals(buildQueryItem(new OrItem(), new AndItem()),
                weakAnd2AndRecurse(buildQueryItem(new OrItem(), new WeakAndItem())));
        assertEquals(buildQueryItem(new AndItem(), new AndItem()),
                weakAnd2AndRecurse(buildQueryItem(new AndItem(), new WeakAndItem())));
    }

    private static WeakAndItem try2Adjust(WeakAndItem item, int hits) {
        adjustWeakAndHeap(item, hits);
        return item;
    }

    private static WeakAndItem try2Adjust(WeakAndItem item, CompositeItem parent, int hits) {
        parent.addItem(item);
        adjustWeakAndHeap(parent, hits);
        return item;
    }

    @Test
    public void requireThatDefaultWeakAndHeapIsAdjustedUpToHits() {
        assertEquals(1000, try2Adjust(new WeakAndItem(), 1000).getN());
        assertFalse(try2Adjust(new WeakAndItem(), 10).nIsExplicit());

        assertEquals(1000, try2Adjust(new WeakAndItem(), new OrItem(), 1000).getN());
        assertFalse(try2Adjust(new WeakAndItem(), new OrItem(), 10).nIsExplicit());
    }
    @Test
    public void requireThatNonDefaultWeakAndHeapIsNotAdjustedUpToHits() {
        assertEquals(33, try2Adjust(new WeakAndItem(33), 1000).getN());
        assertEquals(33, try2Adjust(new WeakAndItem(33), 11).getN());
        assertEquals(WeakAndItem.defaultN, try2Adjust(new WeakAndItem(WeakAndItem.defaultN), 1000).getN());
    }

}
