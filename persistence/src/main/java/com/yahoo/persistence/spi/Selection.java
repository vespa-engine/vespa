// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.select.DocumentSelector;
import com.yahoo.document.select.Result;
import com.yahoo.document.select.parser.ParseException;

import java.util.Set;

/**
 * Class used when iterating to represent a selection of entries to be returned.
 *
 * This class is likely to be replaced by a more generic selection AST in the near future.
 */
public class Selection {
    DocumentSelector documentSelection = null;
    long fromTimestamp = 0;
    long toTimestamp = Long.MAX_VALUE;
    Set<Long> timestampSubset = null;

    public Selection(String documentSelection, long fromTimestamp, long toTimestamp) throws ParseException {
        this.documentSelection = new DocumentSelector(documentSelection);
        this.fromTimestamp = fromTimestamp;
        this.toTimestamp = toTimestamp;
    }

    public Selection(Set<Long> timestampSubset) {
        this.timestampSubset = timestampSubset;
    }

    public boolean requiresFields() {
        return documentSelection != null;
    }

    public Set<Long> getTimestampSubset() {
        return timestampSubset;
    }

    /**
     * Returns true if the entry matches the selection criteria given.
     */
    public boolean match(Document doc, long timestamp) {
        if (timestamp < fromTimestamp) {
            return false;
        }

        if (timestamp > toTimestamp) {
            return false;
        }

        if (timestampSubset != null && !timestampSubset.contains(timestamp)) {
            return false;
        }

        if (documentSelection != null && doc != null && !documentSelection.accepts(new DocumentPut(doc)).equals(Result.TRUE)) {
            return false;
        }

        return true;
    }

    /**
     * Returns true if the entry matches the timestamp ranges/subsets specified in the selection.
     */
    public boolean match(long timestamp) {
        return match(null, timestamp);
    }
}
