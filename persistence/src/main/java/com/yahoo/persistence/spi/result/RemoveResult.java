// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.result;

import com.yahoo.persistence.spi.BucketInfo;

/**
 * Result class for Remove operations
 */
public class RemoveResult extends Result {
    boolean wasFound = false;

    /**
     * Constructor to use when an error occurred during the update
     *
     * @param error The type of error that occurred
     * @param message A human readable message further detailing the error.
     */
    public RemoveResult(Result.ErrorType error, String message) {
        super(error, message);
    }

    /**
     * Constructor to use when there was no document to remove.
     */
    public RemoveResult() {}

    /**
     * Constructor to use when the update was successful.
     *
     * @param wasFound The timestamp of the document that was updated.
     */
    public RemoveResult(boolean wasFound) {
        this.wasFound = wasFound;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof RemoveResult) {
            return super.equals((Result)other) &&
                   wasFound == ((RemoveResult)other).wasFound;
        }

        return false;
    }

    public boolean wasFound() { return wasFound; }
}
