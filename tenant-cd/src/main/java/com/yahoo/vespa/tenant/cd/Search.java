package com.yahoo.vespa.tenant.cd;

import java.util.SortedMap;

/**
 * The immutable result of sending a {@link Query} to a Vespa {@link Endpoint}.
 *
 * @author jonmv
 */
public class Search {

    // hits
    // coverage
    // searched
    // full?
    // results?
    // resultsFull?

    /** Returns the documents that were returned as the result, with an iteration order of decreasing relevance. */
    SortedMap<DocumentId, Document> documents() {
        return null;
    }

}
