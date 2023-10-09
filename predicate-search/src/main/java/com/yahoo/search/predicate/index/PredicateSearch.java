// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.yahoo.search.predicate.Hit;
import com.yahoo.search.predicate.SubqueryBitmap;
import com.yahoo.search.predicate.utils.PrimitiveArraySorter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implementation of the "Interval" predicate search algorithm.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class PredicateSearch {

    private final PostingList[] postingLists;
    private final byte[] nPostingListsForDocument;
    private final byte[] minFeatureIndex;
    private final int[] docIds;
    private final int[] intervals;
    private final long[] subqueries;
    private final long[] subqueryMarkers;
    private final boolean[] visited;
    private final short[] intervalEnds;

    private short[] sortedIndexes;
    private short[] sortedIndexesMergeBuffer;
    private int nPostingLists;

    /**
     * Creates a search for a set of posting lists.
     * 
     * @param postingLists Posting lists for the boolean variables that evaluate to true
     * @param nPostingListsForDocument The number of posting list for each docId
     * @param minFeatureIndex Index from docId to min-feature value.
     * @param intervalEnds The interval end for each document.
     * @param highestIntervalEnd The highest end value.
     */
    public PredicateSearch(
            List<PostingList> postingLists, byte[] nPostingListsForDocument,
            byte[] minFeatureIndex, short[] intervalEnds, int  highestIntervalEnd) {
        int size = postingLists.size();
        this.nPostingListsForDocument = nPostingListsForDocument;
        this.minFeatureIndex = minFeatureIndex;
        this.nPostingLists = size;
        this.postingLists = postingLists.toArray(new PostingList[postingLists.size()]);
        this.sortedIndexes = new short[size];
        this.sortedIndexesMergeBuffer = new short[size];
        this.docIds = new int[size];
        this.intervals = new int[size];
        this.subqueries = new long[size];
        this.subqueryMarkers = new long[highestIntervalEnd + 1];
        this.visited = new boolean[highestIntervalEnd + 1];
        this.intervalEnds = intervalEnds;

        // Sort posting list array based on the underlying number of documents (largest first).
        Arrays.sort(this.postingLists, (l, r) -> -Integer.compare(l.size(), r.size()));

        for (short i = 0; i < size; ++i) {
            PostingList postingList = this.postingLists[i];
            sortedIndexes[i] = i;
            docIds[i] = postingList.getDocId();
            subqueries[i] = postingList.getSubquery();
        }
        // All posting lists start at beginId, so no need to sort yet.
    }

    /**
     * @return A stream of Hit-objects from a lazy evaluation of the boolean search algorithm.
     */
    public Stream<Hit> stream() {
        if (nPostingLists == 0) {
            return Stream.empty();
        }
        return StreamSupport.stream(new PredicateSpliterator(), false);
    }

    private class PredicateSpliterator implements java.util.Spliterator<Hit> {
        private int lastHit = -1;

        @Override
        public boolean tryAdvance(Consumer<? super Hit> action) {
            Optional<Hit> optionalHit = seek(lastHit + 1);
            optionalHit.ifPresent(hit -> {
                lastHit = hit.getDocId();
                action.accept(hit);
            });
            return optionalHit.isPresent();
        }

        @Override
        public Spliterator<Hit> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | DISTINCT | SORTED | NONNULL;
        }

        @Override
        public Comparator<Hit> getComparator() {
            return null;
        }
    }

    private Optional<Hit> seek(int docId) {
        boolean skippedToEnd = skipMinFeature(docId);
        while (nPostingLists > 0 && !skippedToEnd) {
            int docId0 = docIds[sortedIndexes[0]];
            int minFeature = minFeatureIndex[docId0];
            int k = minFeature > 0 ? minFeature - 1 : 0;
            int intervalEnd = Short.toUnsignedInt(intervalEnds[docId0]);
            if (k < nPostingLists) {
                int docIdK = docIds[sortedIndexes[k]];
                if (docId0 == docIdK) {
                    if (evaluateHit(docId0, k, intervalEnd)) {
                        return Optional.of(new Hit(docId0, subqueryMarkers[intervalEnd]));
                    }
                }
            }
            skippedToEnd = skipMinFeature(docId0 + 1);
        }
        return Optional.empty();
    }

    private boolean skipMinFeature(int docId) {
        int nDocuments = nPostingListsForDocument.length;
        while (docId < nDocuments && minFeatureIndex[docId] > nPostingListsForDocument[docId]) {
            ++docId;
        }
        if (docId < nDocuments) {
            advanceAllTo(docId);
            return false;
        }
        return true;
    }

    private boolean evaluateHit(int docId, int k, int intervalEnd) {
        int candidates = k + 1;
        for (int i = candidates; i < nPostingLists; ++i) {
            if (docIds[sortedIndexes[i]] == docId) {
                ++candidates;
            } else {
                break;
            }
        }

        int nNoIntervalIterators = 0;
        for (int i = 0; i < candidates; ++i) {
            short index = sortedIndexes[i];
            PostingList postingList = postingLists[index];
            if (postingList.prepareIntervals()) {
                intervals[index] = postingList.getInterval();
            } else {
                ++nNoIntervalIterators;
                intervals[index] = 0xFFFFFFFF;
            }
        }
        PrimitiveArraySorter.sort(sortedIndexes, 0, candidates, (a, b) -> Integer.compareUnsigned(intervals[a], intervals[b]));
        candidates -= nNoIntervalIterators;

        Arrays.fill(subqueryMarkers, 0, intervalEnd + 1, 0);
        subqueryMarkers[0] = SubqueryBitmap.ALL_SUBQUERIES;
        Arrays.fill(visited, 0, intervalEnd + 1, false);
        visited[0] = true;
        int highestEndSeen = 1;
        for (int i = 0; i < candidates; ) {
            int index = sortedIndexes[i];
            int lastEnd = addInterval(index, highestEndSeen);
            if (lastEnd == -1) {
                return false;
            }
            highestEndSeen = Math.max(lastEnd, highestEndSeen);
            PostingList postingList = postingLists[index];
            if (postingList.nextInterval()) {
                intervals[index] = postingList.getInterval();
                restoreSortedOrder(i, candidates);
            } else {
                ++i;
            }
        }
        return subqueryMarkers[intervalEnd] != 0;
    }

    private void restoreSortedOrder(int first, int last) {
        short indexToMove = sortedIndexes[first];
        long intervalToMove = Integer.toUnsignedLong(intervals[indexToMove]);
        while (++first < last && intervalToMove > Integer.toUnsignedLong(intervals[sortedIndexes[first]])) {
            sortedIndexes[first - 1] = sortedIndexes[first];
        }
        sortedIndexes[first - 1] = indexToMove;
    }

    /**
     * Returns the end value of the interval,
     * or -1 if the highest end value seen is less than the interval begin.
     */
    private int addInterval(int index, int highestEndSeen) {
        int interval = intervals[index];
        long subqueryBitMap = subqueries[index];
        if (Interval.isZStar1Interval(interval)) {
            int begin = Interval.getZStar1Begin(interval);
            int end = Interval.getZStar1End(interval);
            if (highestEndSeen < begin) return -1;
            markSubquery(begin, end, ~subqueryMarkers[begin]);
            return end;
        } else {
            int begin = Interval.getBegin(interval);
            int end = Interval.getEnd(interval);
            if (highestEndSeen < begin -1) return -1;
            markSubquery(begin - 1, end, subqueryMarkers[begin - 1] & subqueryBitMap);
            return end;
        }
    }

    private void markSubquery(int begin, int end, long subqueryBitmap) {
        if (visited[begin]) {
            visited[end] = true;
            subqueryMarkers[end] |= subqueryBitmap;
        }
    }

    // Advances all posting lists to (or beyond) docId.
    private void advanceAllTo(int docId) {
        int i = 0;
        int completedCount = 0;
        for (; i < nPostingLists; ++i) {
            if (docIds[sortedIndexes[i]] >= docId) {
                break;
            }
            if (!advanceOneTo(docId, i)) {
                ++completedCount;
            }
        }
        // No need to sort if all posting lists are finished.
        if (i > 0 && nPostingLists > completedCount) {
            sortIndexes(i);
            // Decrement the number of posting lists.
        }
        nPostingLists -= completedCount;
    }

    // Advances a single posting list to (or beyond) docId.
    private boolean advanceOneTo(int docId, int index) {
        int i = sortedIndexes[index];
        PostingList postingList = postingLists[i];
        if (postingList.nextDocument(docId - 1)) {
            docIds[i] = postingList.getDocId();
            return true;
        }
        docIds[i] = Integer.MAX_VALUE;  // will be last after sorting.
        return false;
    }

    private void sortIndexes(int numUpdated) {
        // Sort the updated elements
        boolean swapMergeBuffer =
                PrimitiveArraySorter.sortAndMerge(sortedIndexes, sortedIndexesMergeBuffer, numUpdated, nPostingLists,
                        (a, b) -> Integer.compare(docIds[a], docIds[b]));
        if (swapMergeBuffer) {
            // Swap references
            short[] temp = sortedIndexes;
            sortedIndexes = sortedIndexesMergeBuffer;
            sortedIndexesMergeBuffer = temp;
        }

    }

}
