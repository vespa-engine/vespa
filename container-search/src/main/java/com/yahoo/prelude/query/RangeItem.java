// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

/**
 * This class represents a numeric range. You can also specify the number of hits you want this range to produce,
 * which can be used to create more efficient searches.
 * Note that '0' as hit limit means all hits matching the range criterion will be a match,
 * while positive numbers start from 'from' working
 * its way towards 'to' until it has reached its limit or range is exhausted. Negative number means that it will start
 * from 'to' and work its way towards 'from'.
 *
 * @author baldersheim
 * @author bratseth
 */
// Note that this is just a convenience subclass of IntItem - it does not add any functionality not available in it.
public class RangeItem extends IntItem {

    /**
     * Creates a new range operator
     *
     * @param from  inclusive start point for range
     * @param to    inclusive end point for range
     * @param indexName the index to search for this range
     */
    public RangeItem(Number from, Number to, String indexName) {
        this(from, to, indexName, false);
    }

    /**
     * Creates a new range operator
     *
     * @param from  start point for range
     * @param to    end point for range
     * @param indexName the index to search for this range
     */
    public RangeItem(Limit from, Limit to, String indexName) {
        this(from, to, indexName, false);
    }

    /**
     * Creates a new range operator
     *
     * @param from  inclusive start point for range
     * @param to    inclusive end point for range
     * @param indexName the index to search for this range
     * @param isFromQuery Indicate if this stems directly from the user given query,
     *                    or if you have constructed it at will.
     */
    public RangeItem(Number from, Number to, String indexName, boolean isFromQuery) {
        this(from, to, 0, indexName, isFromQuery);
    }

    /**
     * Creates a new range operator
     *
     * @param from  start point for range
     * @param to    end point for range
     * @param indexName the index to search for this range
     * @param isFromQuery Indicate if this stems directly from the user given query,
     *                    or if you have constructed it at will.
     */
    public RangeItem(Limit from, Limit to, String indexName, boolean isFromQuery) {
        this(from, to, 0, indexName, isFromQuery);
    }

    /**
     *
     * @param from  inclusive start point for range
     * @param to    inclusive end point for range
     * @param hitLimit This tells how many results you want included from this range as a minimum.
     *                 You might get less if there are not enough, or you might get more. It will use the dictionary and
     *                 include enough entries to satisfy your request.
     *                 Positive number will start from left (@from) and work right.
     *                 Negative number will start from right and work its way left.
     *                 0 means no limit.
     * @param indexName the index to search for this range
     * @param isFromQuery Indicate if this stems directly from the user given query,
     *                    or if you have constructed it at will.
     */
    public RangeItem(Number from, Number to, int hitLimit, String indexName, boolean isFromQuery) {
        this(new Limit(from, true), new Limit(to, true), hitLimit, indexName, isFromQuery);
    }

    /**
     *
     * @param from  start point for range
     * @param to    end point for range
     * @param hitLimit This tells how many results you want included from this range as a minimum.
     *                 You might get less if there are not enough, or you might get more. It will use the dictionary and
     *                 include enough entries to satisfy your request.
     *                 Positive number will start from left (@from) and work right.
     *                 Negative number will start from right and work its way left.
     *                 0 means no limit.
     * @param indexName the index to search for this range
     * @param isFromQuery Indicate if this stems directly from the user given query,
     *                    or if you have constructed it at will.
     */
    public RangeItem(Limit from, Limit to, int hitLimit, String indexName, boolean isFromQuery) {
        super(from, to, hitLimit, indexName, isFromQuery);
    }

    /** Returns the lower limit of this range, which may be negative infinity */
    public final Number getFrom() {
        return getFromLimit().number();
    }

    /** Returns the upper limit of this range, which may be positive infinity */
    public final Number getTo() {
        return getToLimit().number();
    }

}
