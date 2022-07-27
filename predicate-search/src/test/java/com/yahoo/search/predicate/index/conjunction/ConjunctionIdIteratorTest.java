// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index.conjunction;

import com.yahoo.search.predicate.SubqueryBitmap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bjorncs
 */
public class ConjunctionIdIteratorTest {

    @SuppressWarnings("PointlessBitwiseExpression")
    @Test
    void require_that_next_returns_skips_to_correct_value() {
        // NOTE: LST bit represents the conjunction sign: 0 => negative, 1 => positive.
        int[] conjunctionIds = new int[]{
                0 | 1,
                2 | 0,
                4 | 0,
                6 | 1,
                8 | 1,
                10 | 0};

        ConjunctionIdIterator postingList =
                new ConjunctionIdIterator(SubqueryBitmap.ALL_SUBQUERIES, conjunctionIds);

        assertEquals(1, postingList.getConjunctionId());
        assertEquals(1, postingList.getConjunctionId()); // Should not change.

        assertTrue(postingList.next(2));
        assertEquals(2, postingList.getConjunctionId());
        assertTrue(postingList.next(0)); // Should not change current conjunction id
        assertEquals(2, postingList.getConjunctionId());

        assertTrue(postingList.next(6 | 1)); // Should skip past id 4
        assertEquals(7, postingList.getConjunctionId());

        assertTrue(postingList.next(8)); // Should skip to 9
        assertEquals(9, postingList.getConjunctionId());

        assertTrue(postingList.next(10 | 1));
        assertEquals(10, postingList.getConjunctionId());

        assertFalse(postingList.next(12)); // End of posting list
    }

    @Test
    void require_that_subquery_is_correct() {
        ConjunctionIdIterator iterator = new ConjunctionIdIterator(0b1111, new int[]{1});
        assertEquals(0b1111, iterator.getSubqueryBitmap());
    }
}
