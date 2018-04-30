// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.yahoo.search.predicate.SubqueryBitmap;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntervalPostingListTest {

    @Test
    public void requireThatPostingListCanIterate() {
        PredicateIntervalStore.Builder builder = new PredicateIntervalStore.Builder();
        int ref1 = builder.insert(Arrays.asList(0x1ffff));
        int ref2 = builder.insert(Arrays.asList(0x1ffff));
        int ref3 = builder.insert(Arrays.asList(0x10001, 0x2ffff));
        IntervalPostingList postingList = new IntervalPostingList(
                builder.build(), new int[]{2, 4, 6}, new int[] {ref1, ref2, ref3}, SubqueryBitmap.ALL_SUBQUERIES);
        assertEquals(-1, postingList.getDocId());
        assertEquals(0, postingList.getInterval());
        assertEquals(0xffffffffffffffffL, postingList.getSubquery());

        assertTrue(postingList.nextDocument(0));
        assertTrue(postingList.prepareIntervals());
        assertEquals(2, postingList.getDocId());
        assertEquals(0x1ffff, postingList.getInterval());
        assertFalse(postingList.nextInterval());

        assertTrue(postingList.nextDocument(4));
        assertTrue(postingList.prepareIntervals());
        assertEquals(6, postingList.getDocId());
        assertEquals(0x10001, postingList.getInterval());
        assertTrue(postingList.nextInterval());
        assertEquals(0x2ffff, postingList.getInterval());
        assertFalse(postingList.nextInterval());

        assertFalse(postingList.nextDocument(8));
    }

}
