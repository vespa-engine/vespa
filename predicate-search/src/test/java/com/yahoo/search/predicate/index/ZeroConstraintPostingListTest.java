// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class ZeroConstraintPostingListTest {

    @Test
    void requireThatPostingListCanIterate() {
        ZeroConstraintPostingList postingList =
                new ZeroConstraintPostingList(new int[]{2, 4, 6, 8});
        assertEquals(-1, postingList.getDocId());
        assertEquals(Interval.fromBoundaries(1, Interval.ZERO_CONSTRAINT_RANGE), postingList.getInterval());
        assertEquals(0xffffffffffffffffL, postingList.getSubquery());

        assertTrue(postingList.nextDocument(0));
        assertEquals(2, postingList.getDocId());
        assertTrue(postingList.prepareIntervals());
        assertFalse(postingList.nextInterval());

        assertTrue(postingList.nextDocument(7));
        assertEquals(8, postingList.getDocId());

        assertTrue(postingList.nextDocument(7));
        assertEquals(8, postingList.getDocId());

        assertFalse(postingList.nextDocument(8));
    }
}
