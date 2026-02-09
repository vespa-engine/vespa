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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author baldersheim
 */
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
    public void weakAndIsDetected() {
        assertEquals(null, targetHits(new OrItem()));
        assertEquals(null, targetHits(new WeakAndItem(Integer.valueOf(33))));
        assertEquals(null, targetHits(addItem(new WeakAndItem(Integer.valueOf(33)), new TrueItem())));
        assertEquals(33, targetHits(addItem(addItem(new WeakAndItem(Integer.valueOf(33)), new TrueItem()), new TrueItem())));
        assertEquals(77, targetHits(buildQueryItem(new OrItem(), new WeakAndItem(Integer.valueOf(77)))));
        assertEquals(77, targetHits(buildQueryItem(new AndItem(), new WeakAndItem(Integer.valueOf(77)))));
        assertEquals(null, targetHits(buildQueryItem(new OrItem(), new AndItem())));
    }

    @Test
    public void weakAndIsReplacedWithAnd() {
        assertEquals(buildQueryItem(new OrItem(), new AndItem()),
                     weakAnd2AndRecurse(buildQueryItem(new OrItem(), new WeakAndItem())));
        assertEquals(buildQueryItem(new AndItem(), new AndItem()),
                     weakAnd2AndRecurse(buildQueryItem(new AndItem(), new WeakAndItem())));
    }

    @Test
    public void defaultWeakAndHeapIsAdjustedUpToHits() {
        assertEquals(1000, try2Adjust(new WeakAndItem(), 1000).getTargetHits());
        assertNull(try2Adjust(new WeakAndItem(), 10).getTargetHits());

        assertEquals(1000, try2Adjust(new WeakAndItem(), new OrItem(), 1000).getTargetHits());
        assertNull(try2Adjust(new WeakAndItem(), new OrItem(), 10).getTargetHits());
    }

    @Test
    public void nonDefaultWeakAndHeapIsNotAdjustedUpToHits() {
        assertEquals(33, try2Adjust(new WeakAndItem(Integer.valueOf(33)), 1000).getTargetHits());
        assertEquals(33, try2Adjust(new WeakAndItem(Integer.valueOf(33)), 11).getTargetHits());
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

}
