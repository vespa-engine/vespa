// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

/**
 * Interface for posting lists to be used by the algorithm implemented in PredicateSearch.
 *
 * @author Magnar Nedland
 */
public interface PostingList {

    /**
     * Moves the posting list past the supplied document id.
     * @param docId Document id to move past.
     * @return True if a new document was found
     */
    boolean nextDocument(int docId);

    /**
     * Prepare iterator for interval iteration.
     * @return True if the iterator has any intervals.
     */
    boolean prepareIntervals();

    /**
     * Fetches the next interval for the current document.
     * @return True if there was a next interval
     */
    boolean nextInterval();

    /**
     * @return The doc id for the current document
     */
    int getDocId();

    /**
     * @return The number of documents (actual count or estimate)
     */
    int size();

    /**
     * @return The current interval for the current document
     */
    int getInterval();

    /**
     * @return the subquery bitmap for this posting list.
     */
    long getSubquery();

    /**
     * @return The document ids
     */
    int[] getDocIds();

}
