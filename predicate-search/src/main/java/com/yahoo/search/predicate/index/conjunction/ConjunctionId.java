// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index.conjunction;

/**
 * Conjunction id format:
 *   bit 31-1:  id/hash
 *   bit 0:     0: negated, 1: not negated
 *
 * @author bjorncs
 */
public class ConjunctionId {

    public static int compare(int c1, int c2) {
        return Integer.compare(c1 | 1, c2 | 1);
    }

    public static boolean equals(int c1, int c2) {
        return (c1 | 1) == (c2 | 1);
    }

    public static boolean isPositive(int c) {
        return (c & 1) == 1;
    }

    public static int nextId(int c) {
        return (c | 1) + 1;
    }

}
