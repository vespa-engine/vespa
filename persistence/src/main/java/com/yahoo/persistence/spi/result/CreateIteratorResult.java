// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.result;

/**
 * Result class for CreateIterator requests.
 */
public class CreateIteratorResult extends Result {
    long iteratorId = 0;

    /**
     * Creates a result with an error.
     *
     * @param type The type of error
     * @param message A human-readable error message to further detail the error.
     */
    public CreateIteratorResult(Result.ErrorType type, String message) {
        super(type, message);
    }

    /**
     * Creates a successful result, containing a unique identifier for this iterator
     * (must be created and maintained by the provider).
     *
     * @param iteratorId The iterator ID to use for this iterator.
     */
    public CreateIteratorResult(long iteratorId) {
        this.iteratorId = iteratorId;
    }

    public long getIteratorId() {
        return iteratorId;
    }
}
