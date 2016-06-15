// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.result;

import com.yahoo.persistence.spi.DocEntry;

import java.util.List;

/**
 * Result class for iterate requests
 */
public class IterateResult extends Result {
    List<DocEntry> entries = null;
    boolean isCompleted = false;

    /**
     * Creates a result with an error.
     *
     * @param type The type of error
     * @param message A human-readable error message to further detail the error.
     */
    public IterateResult(Result.ErrorType type, String message) {
        super(type, message);
    }

    /**
     * Creates a successful result.
     *
     * @param entries The next chunk of entries that were found during iteration.
     * @param isCompleted Set to true if there are no more entries to iterate through.
     */
    public IterateResult(List<DocEntry> entries, boolean isCompleted) {
        this.entries = entries;
        this.isCompleted = isCompleted;
    }

    public List<DocEntry> getEntries() {
        return entries;
    }

    public boolean isCompleted() {
        return isCompleted;
    }
}
