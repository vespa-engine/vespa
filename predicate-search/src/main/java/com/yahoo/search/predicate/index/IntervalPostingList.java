// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

/**
 * Implementation of PostingList for regular features that store
 * their intervals and nothing else.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class IntervalPostingList extends MultiIntervalPostingList {

    private final PredicateIntervalStore store;
    private int[] currentIntervals;
    private int currentIntervalIndex;
    private int currentInterval;

    public IntervalPostingList(PredicateIntervalStore store, int[] docIds, int[] dataRefs, long subquery) {
        super(docIds, dataRefs, subquery);
        this.store = store;
    }

    @Override
    protected boolean prepareIntervals(int dataRef) {
        currentIntervals = store.get(dataRef);
        currentIntervalIndex = 1;
        currentInterval = currentIntervals[0];
        return true;
    }

    @Override
    public boolean nextInterval() {
        if (currentIntervalIndex < currentIntervals.length) {
            this.currentInterval = currentIntervals[currentIntervalIndex++];
            return true;
        }
        return false;
    }

    @Override
    public int getInterval() {
        return currentInterval;
    }

}
