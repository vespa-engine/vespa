// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.result;

import com.yahoo.document.BucketId;

import java.util.List;

/**
 * Result class used for bucket id list requests.
 */
public class BucketIdListResult extends Result {
    List<BucketId> buckets;

    /**
     * Creates a result with an error.
     *
     * @param type The type of error
     * @param message A human-readable error message to further detail the error.
     */
    public BucketIdListResult(ErrorType type, String message) {
        super(type, message);
    }

    /**
     * Creates a result containing a list of all the buckets the requested partition has.
     *
     * @param buckets The list of buckets.
     */
    public BucketIdListResult(List<BucketId> buckets) {
        this.buckets = buckets;
    }

    public List<BucketId> getBuckets() {
        return buckets;
    }
}
