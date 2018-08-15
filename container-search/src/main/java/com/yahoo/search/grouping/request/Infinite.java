// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents an Infinite value that may be used as a bucket
 * size specifier.
 *
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */
@SuppressWarnings("rawtypes")
public class Infinite implements Comparable {
    private final boolean negative;

    /**
     * Create an Infinite object with positive or negative sign.
     * @param negative the signedness.
     */
    public Infinite(boolean negative) {
        this.negative = negative;
    }

    /**
     * Override the toString method in order to be re-parseable.
     */
    @Override
    public String toString() {
        return (negative ? "-inf" : "inf");
    }

    /**
     * An infinity value is always less than or greater than.
     */
    @Override
    public int compareTo(Object rhs) {
        return (negative ? -1 : 1);
    }
}
