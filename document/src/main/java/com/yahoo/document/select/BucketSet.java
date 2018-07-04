// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.BucketId;

import java.util.HashSet;

/**
 * A set of bucket ids covered by a document selector.
 *
 * @author Simon Thoresen Hult
 */
public class BucketSet extends HashSet<BucketId> {

    /**
     * Constructs a new bucket set that contains no ids.
     */
    public BucketSet() {
        // empty
    }

    /**
     * Constructs a new bucket set that contains a single id.
     *
     * @param id The id to add to this as initial value.
     */
    public BucketSet(BucketId id) {
        add(id);
    }

    /**
     * Constructs a new bucket set that is a copy of another.
     *
     * @param set The set to copy.
     */
    public BucketSet(BucketSet set) {
        this.addAll(set);
    }

    /**
     * Returns the intersection between this bucket set and another.
     *
     * @param rhs The set to form an intersection with.
     * @return The intersection.
     */
    public BucketSet intersection(BucketSet rhs) {
        if (rhs == null) {
            return new BucketSet(this); // The other has all buckets marked, this is the smaller.
        } else {
            BucketSet ret = new BucketSet(this);
            ret.retainAll(rhs);
            return ret;
        }
    }

    /**
     * Returns the union between this bucket set and another.
     *
     * @param rhs The set to form a union with.
     * @return The union.
     */
    public BucketSet union(BucketSet rhs) {
        if (rhs == null) {
            return null;
        } else {
            BucketSet ret = new BucketSet(this);
            ret.addAll(rhs);
            return ret;
        }
    }

}
