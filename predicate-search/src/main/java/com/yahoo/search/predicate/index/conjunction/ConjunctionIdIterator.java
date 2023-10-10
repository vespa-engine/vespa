// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index.conjunction;

/**
 * Conjunction id posting list iterator for a single feature/assignment (e.g. a=b).
 *
 * @author bjorncs
 */
public class ConjunctionIdIterator {

    private final int[] conjunctionIds;
    private final long subqueryBitmap;
    private int currentConjunctionId;
    private int length;
    private int index;

    public ConjunctionIdIterator(long subqueryBitmap, int[] conjunctionIds) {
        this.subqueryBitmap = subqueryBitmap;
        this.conjunctionIds = conjunctionIds;
        this.currentConjunctionId = conjunctionIds[0];
        this.length = conjunctionIds.length;
        this.index = 0;
    }

    public boolean next(int conjunctionId) {
        if (index == length) return false;

        int candidate = currentConjunctionId;
        while (ConjunctionId.compare(conjunctionId, candidate) > 0 && ++index < length) {
            candidate = conjunctionIds[index];
        }
        currentConjunctionId = candidate;
        return ConjunctionId.compare(conjunctionId, candidate) <= 0;
    }

    public long getSubqueryBitmap() {
        return subqueryBitmap;
    }

    public int getConjunctionId() {
        return currentConjunctionId;
    }

    public int[] getConjunctionIds() {
        return conjunctionIds;
    }

}
