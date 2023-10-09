// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.yahoo.search.predicate.SubqueryBitmap;

/**
 * Wraps an int stream of document ids into a PostingList.
 * All documents in the stream are considered matches.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public class ZeroConstraintPostingList implements PostingList {

    private final int[] docIds;
    private final int length;
    private int currentIndex;
    private int currentDocId;

    public ZeroConstraintPostingList(int[] docIds) {
        this.docIds = docIds;
        this.currentIndex = 0;
        this.currentDocId = -1;
        this.length = docIds.length;
    }

    @Override
    public boolean nextDocument(int docId) {
        int currentDocId = this.currentDocId;
        while (currentIndex < length && currentDocId <= docId) {
            currentDocId = docIds[currentIndex++];
        }
        if (currentDocId <= docId) {
            return false;
        }
        this.currentDocId = currentDocId;
        return true;
    }

    @Override
    public boolean prepareIntervals() {
        return true;
    }

    @Override
    public boolean nextInterval() {
        return false;
    }

    @Override
    public int size() {
        return length;
    }

    @Override
    public int getInterval() {
        return Interval.fromBoundaries(1, Interval.ZERO_CONSTRAINT_RANGE);
    }

    @Override
    public int getDocId() {
        return currentDocId;
    }

    @Override
    public long getSubquery() {
        return SubqueryBitmap.ALL_SUBQUERIES;
    }

    @Override
    public int[] getDocIds() {
        return docIds;
    }

}
