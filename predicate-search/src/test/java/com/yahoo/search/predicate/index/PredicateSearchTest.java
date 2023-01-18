// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.yahoo.search.predicate.Hit;
import com.yahoo.search.predicate.SubqueryBitmap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 * @author bjorncs
 */
public class PredicateSearchTest {

    @Test
    void requireThatNoStreamsReturnNoResults() {
        PredicateSearch search = new PredicateSearch(new ArrayList<>(), new byte[0], new byte[0], new short[0], 1);
        assertEquals(0, search.stream().count());
    }

    @Test
    void requireThatSingleStreamFiltersOnConstructedCompleteIntervals() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1, 1, 1},
                postingList(
                        SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x000100ff),
                        entry(1, 0x00010001, 0x000200ff),
                        entry(2, 0x00010042)));
        assertEquals(Arrays.asList(new Hit(0), new Hit(1)).toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatMinFeatureIsUsedToPruneResults() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{3, 1},
                postingList(
                        SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x000100ff),
                        entry(1, 0x000100ff)));
        assertEquals(Arrays.asList(new Hit(1)).toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatAHighKCanYieldResults() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{2},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010001)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x000200ff)));
        assertEquals(Arrays.asList(new Hit(0)).toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatPostingListsAreSortedAfterAdvancing() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{2, 1, 1, 1},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x000100ff),
                        entry(3, 0x000100ff)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(1, 0x000100ff),
                        entry(2, 0x000100ff)));
        assertEquals(Arrays.asList(new Hit(1), new Hit(2), new Hit(3)).toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatEmptyPostingListsWork() {
        PredicateSearch search = createPredicateSearch(
                new byte[0],
                postingList(SubqueryBitmap.ALL_SUBQUERIES));
        assertEquals(Arrays.asList().toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatShorterPostingListEndingIsOk() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1, 1, 1},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x000100ff),
                        entry(1, 0x000100ff)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(2, 0x000100ff)));
        assertEquals(Arrays.asList(new Hit(0), new Hit(1), new Hit(2)).toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatSortingWorksForManyPostingLists() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1, 5, 2, 2},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x000100ff),
                        entry(1, 0x000100ff)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(1, 0x000100ff),
                        entry(2, 0x000100ff)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(1, 0x000100ff),
                        entry(3, 0x000100ff)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(1, 0x000100ff),
                        entry(2, 0x000100ff)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(1, 0x000100ff),
                        entry(3, 0x000100ff)));
        assertEquals(
                Arrays.asList(new Hit(0), new Hit(1), new Hit(2), new Hit(3)).toString(),
                search.stream().toList().toString());
    }

    @Test
    void requireThatInsufficientIntervalCoveragePreventsMatch() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1, 1},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010001),
                        entry(1, 0x000200ff)));
        assertEquals(Arrays.asList().toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatIntervalsAreSorted() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010001)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x000300ff)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00020002)));
        assertEquals(Arrays.asList(new Hit(0)).toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatThereCanBeManyIntervals() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010001, 0x00020002, 0x00030003, 0x000100ff, 0x00040004, 0x00050005, 0x00060006)));
        assertEquals(Arrays.asList(new Hit(0)).toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatNotIsSupported_NoMatch() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010001)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010000, 0x00ff0001)));
        assertEquals(Arrays.asList().toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatNotIsSupported_Match() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010000, 0x00ff0001)));
        assertEquals(Arrays.asList(new Hit(0)).toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatNotIsSupported_NoMatchBecauseOfPreviousTerm() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00020001, 0x00ff0001)));
        assertEquals(Arrays.asList().toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatIntervalSortingWorksAsUnsigned() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{1},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010001)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00fe0001, 0x00ff00fe)));
        assertEquals(Arrays.asList(new Hit(0)).toString(), search.stream().toList().toString());
    }

    @Test
    void requireThatMatchCanRequireMultiplePostingLists() {
        PredicateSearch search = createPredicateSearch(
                new byte[]{6},
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010001)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x0002000b, 0x00030003)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00040003)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00050004)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00010008, 0x00060006)),
                postingList(SubqueryBitmap.ALL_SUBQUERIES,
                        entry(0, 0x00020002, 0x000700ff)));
        assertEquals(Arrays.asList(new Hit(0)).toString(), search.stream().toList().toString());
    }

    private static PredicateSearch createPredicateSearch(byte[] minFeatures, PostingList... postingLists) {
        byte[] nPostingListsForDocument = new byte[minFeatures.length];
        short[] intervalEnds = new short[minFeatures.length];
        Arrays.fill(intervalEnds, (short) 0xFF);
        List<PostingList> list = Arrays.asList(postingLists);
        for (PostingList postingList : postingLists) {
            for (int id : postingList.getDocIds()) {
                nPostingListsForDocument[id]++;
            }
        }
        return new PredicateSearch(list, nPostingListsForDocument, minFeatures, intervalEnds, 0xFF);
    }

    private static class SimplePostingList implements PostingList {
        private final long subquery;
        private final Entry[] entries;
        private int[] currentIntervals;
        private int currentIntervalIndex;
        private int currentDocId;
        private int currentIndex;

        public SimplePostingList(long subquery, Entry... entries) {
            this.subquery = subquery;
            this.entries = entries;
            this.currentIndex = 0;
            this.currentDocId = -1;
        }

        @Override
        public boolean nextDocument(int docId) {
            while (currentIndex < entries.length && entries[currentIndex].docId <= docId) {
                ++currentIndex;
            }
            if (currentIndex == entries.length) {
                return false;
            }
            Entry entry = entries[currentIndex];
            currentDocId = entry.docId;
            currentIntervals = entry.intervals;
            currentIntervalIndex = 0;
            return true;
        }

        @Override
        public boolean prepareIntervals() {
            return true;
        }

        @Override
        public boolean nextInterval() {
            return ++currentIntervalIndex < currentIntervals.length;
        }

        @Override
        public int getDocId() {
            return currentDocId;
        }

        @Override
        public int size() {
            return entries.length;
        }

        @Override
        public int getInterval() {
            return currentIntervals[currentIntervalIndex];
        }

        @Override
        public long getSubquery() {
            return subquery;
        }

        @Override
        public int[] getDocIds() {
            return Arrays.stream(entries).mapToInt(e -> e.docId).toArray();
        }

        public static class Entry {
            public final int docId;
            public final int[] intervals;

            public Entry(int docId, int... intervals) {
                this.docId = docId;
                this.intervals = intervals;
            }
        }
    }

    private static SimplePostingList postingList(long subquery, SimplePostingList.Entry... entries) {
        return new SimplePostingList(subquery, entries);
    }

    private static SimplePostingList.Entry entry(int docId, int... intervals) {
        return new SimplePostingList.Entry(docId, intervals);
    }
}
