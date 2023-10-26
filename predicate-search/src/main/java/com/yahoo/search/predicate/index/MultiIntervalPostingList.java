// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

import com.yahoo.search.predicate.utils.PostingListSearch;

/**
 * Shared implementation for posting lists that may have multiple intervals.
 *
 * @author Magnar Nedland
 * @author bjorncs
 */
public abstract class MultiIntervalPostingList implements PostingList {

    private final int[] docIds;
    private final int[] dataRefs;
    private final long subquery;
    private final int length;
    private int currentIndex;
    private int currentDocId;

    public MultiIntervalPostingList(int[] docIds, int[] dataRefs, long subquery) {
        this.docIds = docIds;
        this.dataRefs = dataRefs;
        this.subquery = subquery;
        this.length = docIds.length;
        this.currentIndex = 0;
        this.currentDocId = -1;
    }

    @Override
    public final boolean nextDocument(int docId) {
        int index = currentIndex;
        index = PostingListSearch.interpolationSearch(docIds, index, length, docId);
        if (index == length) {
            return false;
        }
        this.currentDocId = docIds[index];
        this.currentIndex = index;
        assert currentDocId > docId;
        return true;
    }

    @Override
    public final boolean prepareIntervals() {
        return prepareIntervals(dataRefs[currentIndex]);
    }

    protected abstract boolean prepareIntervals(int dataRef);

    @Override
    public final int size() {
        return length;
    }

    @Override
    public final int getDocId() {
        return currentDocId;
    }

    @Override
    public final int[] getDocIds() {
        return docIds;
    }

    @Override
    public final long getSubquery() {
        return subquery;
    }

}
