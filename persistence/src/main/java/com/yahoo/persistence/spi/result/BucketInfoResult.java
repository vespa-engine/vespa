// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.result;

import com.yahoo.persistence.spi.BucketInfo;

/**
 * Result class for the getBucketInfo() function.
 */
public class BucketInfoResult extends Result {
    BucketInfo bucketInfo = null;

    /**
     * Constructor to use for a result where an error has been detected.
     * The service layer will not update the bucket information in this case,
     * so it should not be returned either.
     *
     * @param type The type of error.
     * @param message A human readable message further detailing the error.
     */
    public BucketInfoResult(ErrorType type, String message) {
        super(type, message);
    }

    /**
     * Constructor to use when the write operation was successful,
     * and the bucket info was modified.
     *
     * @param info Returns the information about the bucket.
     */
    public BucketInfoResult(BucketInfo info) {
        this.bucketInfo = info;
    }

    public BucketInfo getBucketInfo() {
        return bucketInfo;
    }
}
