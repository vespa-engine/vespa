// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

/**
 * A wrapper for hit counts, modelled after a search system.
 * Advanced database searches and similar could use these
 * structures as well.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class HitCounts {

    // see the javadoc for the accessors for short comments on each field
    private final int retrievedHits;
    private final int summaryCount;
    private final long totalHitCount;
    private final int requestedHits;
    private final int requestedOffset;
    private final Coverage coverage;

    HitCounts(int retrievedHits, int summaryCount, long totalHitCount, int requestedHits, int requestedOffset) {
        this(retrievedHits, summaryCount, totalHitCount, requestedHits, requestedOffset,
             new Coverage(1,1,1,0));
    }

    public HitCounts(int retrievedHits, int summaryCount, long totalHitCount,
                     int requestedHits, int requestedOffset, Coverage coverage)
    {

        this.retrievedHits = retrievedHits;
        this.summaryCount = summaryCount;
        this.totalHitCount = totalHitCount;
        this.requestedHits = requestedHits;
        this.requestedOffset = requestedOffset;
        this.coverage = coverage;
    }

    /**
     * The number of hits returned by the server.
     * Compare to getRequestedHits().
     */
    public int getRetrievedHitCount() {
        return retrievedHits;
    }

    /**
     * The number of hit summaries ("document contents") fetched.
     */
    public int getSummaryCount() {
        return summaryCount;
    }

    /**
     * The total number of matching hits
     * for the request.
     */
    public long getTotalHitCount() {
        return totalHitCount;
    }

    /**
     * The number of hits requested by the user.
     * Compare to getRetrievedHitCount().
     */
    public int getRequestedHits() {
        return requestedHits;
    }

    /**
     * The user requested offset into the linear mapping of the result space.
     */
    public int getRequestedOffset() {
        return requestedOffset;
    }

    public Coverage getCoverage() { return coverage; }

}
