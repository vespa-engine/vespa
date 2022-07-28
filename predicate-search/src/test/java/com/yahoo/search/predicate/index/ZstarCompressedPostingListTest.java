// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class ZstarCompressedPostingListTest {
    @Test
    void requireThatPostingListCanIterate() {
        PredicateIntervalStore.Builder builder = new PredicateIntervalStore.Builder();
        int ref1 = builder.insert(Arrays.asList(0x10000));
        int ref2 = builder.insert(Arrays.asList(0x10000, 0x0ffff));
        int ref3 = builder.insert(Arrays.asList(0x10000, 0x00003, 0x40003, 0x60005));
        ZstarCompressedPostingList postingList = new ZstarCompressedPostingList(
                builder.build(), new int[]{2, 4, 6}, new int[]{ref1, ref2, ref3});
        assertEquals(-1, postingList.getDocId());
        assertEquals(0, postingList.getInterval());
        assertEquals(0xffffffffffffffffL, postingList.getSubquery());

        assertTrue(postingList.nextDocument(0));
        assertTrue(postingList.prepareIntervals());
        assertEquals(2, postingList.getDocId());
        assertEquals(0x10000, postingList.getInterval());
        assertTrue(postingList.nextInterval());
        assertEquals(0x20001, postingList.getInterval());
        assertFalse(postingList.nextInterval());

        assertTrue(postingList.nextDocument(2));
        assertTrue(postingList.prepareIntervals());
        assertEquals(4, postingList.getDocId());
        assertEquals(0x00010000, postingList.getInterval());
        assertTrue(postingList.nextInterval());
        assertEquals(0xffff0001, postingList.getInterval());
        assertFalse(postingList.nextInterval());

        assertTrue(postingList.nextDocument(4));
        assertTrue(postingList.prepareIntervals());
        assertEquals(6, postingList.getDocId());
        assertEquals(0x10000, postingList.getInterval());
        assertTrue(postingList.nextInterval());
        assertEquals(0x30001, postingList.getInterval());
        assertTrue(postingList.nextInterval());
        assertEquals(0x40003, postingList.getInterval());
        assertTrue(postingList.nextInterval());
        assertEquals(0x50004, postingList.getInterval());
        assertTrue(postingList.nextInterval());
        assertEquals(0x60005, postingList.getInterval());
        assertTrue(postingList.nextInterval());
        assertEquals(0x70006, postingList.getInterval());
        assertFalse(postingList.nextInterval());

        assertFalse(postingList.nextDocument(6));
    }
}
