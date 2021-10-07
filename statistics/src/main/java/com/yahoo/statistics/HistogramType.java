// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


/**
 * Enumeration of how a histogram should be represented from
 * admin server.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public final class HistogramType {
    private final String representation;

    private HistogramType(String representation) {
        this.representation = representation;
    }

    public String toString() { return representation; }

    /**
     * Basic histograms, each bucket is count representing the bucket's
     * defined interval.
     */
    public static final HistogramType REGULAR = new HistogramType("REGULAR");

    /**
     * Cumulative histograms, that is, a given bucket contains the count
     * for values corresponding to "itself" and all preceding buckets.
     */
    public static final HistogramType CUMULATIVE =
        new HistogramType("CUMULATIVE");


    /**
     * Reverse cumulative histograms, that is, a given bucket contains
     * the count for values corresponding to "itself" and all following
     * buckets.
     */
    public static final HistogramType REVERSE_CUMULATIVE =
        new HistogramType("REVERSE_CUMULATIVE");

}
