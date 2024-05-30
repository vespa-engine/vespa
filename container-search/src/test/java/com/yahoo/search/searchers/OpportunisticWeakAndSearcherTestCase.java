// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class OpportunisticWeakAndSearcherTestCase {
    private static Item buildQueryItem(CompositeItem root, CompositeItem injectAtLevel2) {
        root.addItem(new WordItem("text"));
        injectAtLevel2.addItem(new WordItem("a"));
        injectAtLevel2.addItem(new WordItem("b"));
        root.addItem(injectAtLevel2);
        return root;
    }

    @Test
    public void requireThatWeakAndIsDetected() {
        assertEquals(-1, OpportunisticWeakAndSearcher.targetHits(new OrItem()));
        assertEquals(33, OpportunisticWeakAndSearcher.targetHits(new WeakAndItem(33)));
        assertEquals(77, OpportunisticWeakAndSearcher.targetHits(buildQueryItem(new OrItem(), new WeakAndItem(77))));
        assertEquals(77, OpportunisticWeakAndSearcher.targetHits(buildQueryItem(new AndItem(), new WeakAndItem(77))));
        assertEquals(-1, OpportunisticWeakAndSearcher.targetHits(buildQueryItem(new OrItem(), new AndItem())));
    }

    @Test
    public void requireThatWeakAndIsReplacedWithAnd() {
        assertEquals(buildQueryItem(new OrItem(), new AndItem()),
                OpportunisticWeakAndSearcher.weakAnd2AndRecurse(buildQueryItem(new OrItem(), new WeakAndItem())));
        assertEquals(buildQueryItem(new AndItem(), new AndItem()),
                OpportunisticWeakAndSearcher.weakAnd2AndRecurse(buildQueryItem(new AndItem(), new WeakAndItem())));
    }

}
