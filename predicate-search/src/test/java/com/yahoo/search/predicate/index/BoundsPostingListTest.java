// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.google.common.primitives.Ints;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test lies in com.yahoo.search.predicate to get access to some methods of PredicateIndex.
 *
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
public class BoundsPostingListTest {

    @Test
    void requireThatPostingListChecksBounds() {
        PredicateIntervalStore.Builder builder = new PredicateIntervalStore.Builder();
        List<Integer> docIds = new ArrayList<>();
        List<Integer> dataRefs = new ArrayList<>();
        for (int id = 1; id < 100; ++id) {
            List<IntervalWithBounds> boundsList = new ArrayList<>();
            for (int i = 0; i <= id; ++i) {
                int bounds;
                if (id < 30) {
                    bounds = 0x80000000 | i;  // diff >= i
                } else if (id < 60) {
                    bounds = 0x40000000 | i;  // diff < i
                } else {
                    bounds = (i << 16) | (i + 10);  // i < diff < i + 10
                }
                boundsList.add(new IntervalWithBounds((i + 1) << 16 | 0xffff, bounds));
            }
            docIds.add(id);
            dataRefs.add(builder.insert(boundsList.stream().flatMap(IntervalWithBounds::stream).toList()));
        }

        PredicateIntervalStore store = builder.build();
        BoundsPostingList postingList = new BoundsPostingList(
                store, Ints.toArray(docIds), Ints.toArray(dataRefs), 0xffffffffffffffffL, 5);
        assertEquals(-1, postingList.getDocId());
        assertEquals(0, postingList.getInterval());
        assertEquals(0xffffffffffffffffL, postingList.getSubquery());

        checkNext(postingList, 0, 1, 2);  // [0..] .. [1..]
        checkNext(postingList, 1, 2, 3);  // [0..] .. [2..]
        checkNext(postingList, 10, 11, 6);  // [0..] .. [5..]
        checkNext(postingList, 20, 21, 6);

        checkNext(postingList, 30, 31, 26);  // [..5] .. [..30]
        checkNext(postingList, 50, 51, 46);

        checkNext(postingList, 60, 61, 6);  // [0..10] .. [5..15]

        postingList = new BoundsPostingList(store, Ints.toArray(docIds), Ints.toArray(dataRefs), 0xffffffffffffffffL, 40);
        checkNext(postingList, 0, 1, 2);
        checkNext(postingList, 20, 21, 22);

        checkNext(postingList, 30, 31, 0);  // skip ahead to match
        checkNext(postingList, 32, 33, 0);  // skip ahead to match
        checkNext(postingList, 33, 34, 0);  // skip ahead to match
        checkNext(postingList, 40, 41, 1);
        checkNext(postingList, 50, 51, 11);  // [..40] .. [..50]

        checkNext(postingList, 60, 61, 10);  // [31..40] .. [40..49]
    }

    private void checkNext(BoundsPostingList postingList, int movePast, int docId, int intervalCount) {
        assertTrue(postingList.nextDocument(movePast), "Unable to move past " + movePast);
        assertEquals(intervalCount > 0, postingList.prepareIntervals());
        assertEquals(docId, postingList.getDocId());
        for (int i = 0; i < intervalCount - 1; ++i) {
            assertTrue(postingList.nextInterval(), "Too few intervals, expected " + intervalCount);
        }
        assertFalse(postingList.nextInterval(), "Too many intervals, expected " + intervalCount);
    }

}
