// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

/**
 * Wraps a posting stream of IntervalWithBounds objects (for collapsed
 * fixed tree leaf nodes) into a PostingList.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class BoundsPostingList extends MultiIntervalPostingList {
    private final int valueDiff;
    private final IntervalWithBounds intervalWithBounds = new IntervalWithBounds();
    private final PredicateIntervalStore store;
    private int currentInterval;

    /**
     * @param valueDiff Difference from the collapsed leaf node's actual value.
     */
    public BoundsPostingList(PredicateIntervalStore store, int[] docIds, int[] dataRefs, long subquery, int valueDiff) {
        super(docIds, dataRefs, subquery);
        this.valueDiff = valueDiff;
        this.store = store;
    }

    @Override
    protected boolean prepareIntervals(int dataRef) {
        intervalWithBounds.setIntervalArray(store.get(dataRef), 0);
        return nextInterval();
    }

    @Override
    public boolean nextInterval() {
        while (intervalWithBounds.hasValue()) {
            if (intervalWithBounds.contains(valueDiff)) {
                this.currentInterval = intervalWithBounds.getInterval();
                intervalWithBounds.nextValue();
                return true;
            }
            intervalWithBounds.nextValue();
        }
        return false;
    }

    @Override
    public int getInterval() {
        return currentInterval;
    }
}
