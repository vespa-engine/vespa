// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.result;

import com.yahoo.persistence.spi.BucketInfo;

/**
 * Result class for update operations.
 */
public class UpdateResult extends Result {
    long existingTimestamp = 0;

    /**
     * Constructor to use when an error occurred during the update
     *
     * @param error The type of error that occurred
     * @param message A human readable message further detailing the error.
     */
    public UpdateResult(ErrorType error, String message) {
        super(error, message);
    }

    /**
     * Constructor to use when the document to update was not found.
     */
    public UpdateResult() {
        super();
    }

    /**
     * Constructor to use when the update was successful.
     *
     * @param existingTimestamp The timestamp of the document that was updated.
     */
    public UpdateResult(long existingTimestamp) {
        this.existingTimestamp = existingTimestamp;
    }

    public long getExistingTimestamp() { return existingTimestamp; }
}
