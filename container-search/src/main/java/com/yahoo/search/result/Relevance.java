// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

/**
 * A relevance double value. These values should ideally be normalized between 0 and 1 (where 1 means "perfect"),
 * however this is not enforced.
 * <p>
 * Sources may create subclasses of this to include additional information or functionality.
 *
 * @author bratseth
 */
public class Relevance implements Comparable<Relevance> {

    /** The relevancy score. */
    private double score;

    /**
     * Construct a relevancy object with an initial value.
     * This initial value should ideally be a normalized value
     * between 0 and 1, but that is not enforced.
     *
     * @param score the initial value (rank score)
     */
    public Relevance(double score) {
        this.score = score;
    }

    /**
     * Set score value to this value. This should ideally be a
     * normalized value between 0 and 1, but that is not enforced.
     * NaN is also a legal value, for elements where it makes no sense to assign a particular value.
     */
    public void setScore(double score) { this.score = score; }

    /**
     * Returns the relevancy score of this, preferably a normalized value
     * between 0 and 1 but this is not guaranteed by this framework
     */
    public double getScore() { return score; }

    /**
     * Returns the score value as a string
     */
    @Override
    public String toString() {
        return String.valueOf(score);
    }

    /** Compares relevancy values with */
    @Override
    public int compareTo(Relevance other) {
        double thisScore = getScore();
        double otherScore = other.getScore();
        if (Double.isNaN(thisScore)) {
            return Double.isNaN(otherScore) ? 0 : -1;
        } else if (Double.isNaN(otherScore)) {
            return 1;
        } else {
            return Double.compare(thisScore, otherScore);
        }
    }

    /** Compares relevancy values */
    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Relevance)) { return false; }
        return this.compareTo((Relevance)other) == 0;
    }

    /** Returns a hash from the relevancy value */
    @Override
    public int hashCode() {
        return Double.hashCode(score);
    }

}
