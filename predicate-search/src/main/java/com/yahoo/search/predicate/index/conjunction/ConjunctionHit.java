// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index.conjunction;

import com.yahoo.search.predicate.SubqueryBitmap;

/**
 * Represents a conjunction hit. See {@link ConjunctionIndex}.
 *
 * @author bjorncs
 */
public class ConjunctionHit implements Comparable<ConjunctionHit> {

    public final long conjunctionId;
    public final long subqueryBitmap;

    public ConjunctionHit(long conjunctionId, long subqueryBitmap) {
        this.conjunctionId = conjunctionId;
        this.subqueryBitmap = subqueryBitmap;
    }

    @Override
    public int compareTo(ConjunctionHit other) {
        return Long.compareUnsigned(conjunctionId, other.conjunctionId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConjunctionHit that = (ConjunctionHit) o;

        if (conjunctionId != that.conjunctionId) return false;
        return subqueryBitmap == that.subqueryBitmap;

    }

    @Override
    public int hashCode() {
        int result = (int) (conjunctionId ^ (conjunctionId >>> 32));
        result = 31 * result + (int) (subqueryBitmap ^ (subqueryBitmap >>> 32));
        return result;
    }

    @Override
    public String toString() {
        if (subqueryBitmap == SubqueryBitmap.DEFAULT_VALUE) {
            return "" + conjunctionId;
        } else {
            return "[" + conjunctionId + ",0x" + Long.toHexString(subqueryBitmap) + "]";
        }
    }

}
