// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import java.util.List;


/**
 * The innermost part of a histogram, a bucket which only contains a
 * counter.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
class Sum implements Bucket {
    private long sum = 0L;
    private double lower;
    private double upper;

    Sum(double lower, double upper) {
        this.lower = lower;
        this.upper = upper;
    }

    /**
     * Increment this bucket.
     */
    public void put(double[] value, int dim) {
        sum += 1;
    }

    /**
     * Set this bucket's count to 0.
     */
    public void reset() {
        sum = 0L;
    }

    /**
     * The lower limit for values counted by this bucket.
     */
    public double lowerLimit() {
        return lower;
    }

    /**
     * The upper limit for values counted by this bucket.
     */
    public double upperLimit() {
        return upper;
    }

    public String toString() {
        return Long.toString(sum);
    }

    @Override
    public List<Bucket> getBuckets() {
        return null;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public long getSum() {
        return sum;
    }

    @Override
    public void add(long n) {
        sum += n;
    }
}
