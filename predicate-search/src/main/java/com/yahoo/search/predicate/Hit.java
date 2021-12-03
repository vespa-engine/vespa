// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

import com.yahoo.api.annotations.Beta;

/**
 * Represents a hit from the predicate search algorithm.
 * Each hit is associated with a subquery bitmap,
 * indicating which subqueries the hit represents.
 *
 * @author Magnar Nedland
 */
@Beta
public class Hit implements Comparable<Hit> {

    private final int docId;
    private final long subquery;

    public Hit(int docId) {
        this(docId, SubqueryBitmap.DEFAULT_VALUE);
    }

    public Hit(int docId, long subquery) {
        this.docId = docId;
        this.subquery = subquery;
    }

    @Override
    public String toString() {
        if (subquery == SubqueryBitmap.DEFAULT_VALUE) {
            return "" + docId;
        } else {
            return "[" + docId + ",0x" + Long.toHexString(subquery) + "]";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Hit hit = (Hit) o;

        if (docId != hit.docId) return false;
        if (subquery != hit.subquery) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = docId;
        result = 31 * result + (int) (subquery ^ (subquery >>> 32));
        return result;
    }

    public int getDocId() {
        return docId;
    }

    public long getSubquery() {
        return subquery;
    }

    @Override
    public int compareTo(Hit o) {
        return Integer.compare(docId, o.docId);
    }

}
