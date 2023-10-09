// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.yahoo.search.predicate.SubqueryBitmap;

/**
 * Wraps a posting list of compressed NOT-features.
 * The compression works by implying an interval of size 1 after each
 * stored interval, unless the next interval starts with 16 bits of 0,
 * in which case the current interval is extended to the next.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class ZstarCompressedPostingList extends MultiIntervalPostingList {

    private final PredicateIntervalStore store;
    private int[] currentIntervals;
    private int currentIntervalIndex;
    private int prevInterval;
    private int currentInterval;


    /**
     * @param docIds Posting list as a stream.
     */
    public ZstarCompressedPostingList(PredicateIntervalStore store, int[] docIds, int[] dataRefs) {
        super(docIds, dataRefs, SubqueryBitmap.ALL_SUBQUERIES);
        this.store = store;
    }

    @Override
    protected boolean prepareIntervals(int dataRef) {
        currentIntervals = store.get(dataRef);
        currentIntervalIndex = 0;
        return nextInterval();
    }

    @Override
    public boolean nextInterval() {
        int nextInterval = -1;
        if (currentIntervalIndex < currentIntervals.length) {
            nextInterval = currentIntervals[currentIntervalIndex];
        }
        if (prevInterval != 0) {
            if (Interval.isZStar2Interval(nextInterval)) {
                this.currentInterval = Interval.combineZStarIntervals(prevInterval, nextInterval);
                ++currentIntervalIndex;
            } else {
                int end = Interval.getZStar1End(prevInterval);
                this.currentInterval = Interval.fromZStar1Boundaries(end, end + 1);
            }
            prevInterval = 0;
            return true;
        } else if (nextInterval != -1) {
            this.currentInterval = nextInterval;
            ++currentIntervalIndex;
            prevInterval = nextInterval;
            return true;
        }
        return false;
    }

    @Override
    public int getInterval() {
        return currentInterval;
    }

}
