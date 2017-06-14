// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.result;

import com.yahoo.document.Document;

/**
 * Result class for Get operations
 */
public class GetResult extends Result {
    Document doc;
    long lastModifiedTimestamp = 0;

    /**
     * Constructor to use when there was an error retrieving the document.
     * Not finding the document is not an error in this context.
     *
     * @param type The type of error.
     * @param message A human readable message further detailing the error.
     */
    GetResult(ErrorType type, String message) {
        super(type, message);
    }

    /**
     * Constructor to use when we didn't find the document in question.
     */
    public GetResult() {}

    /**
     * Constructor to use when we found the document asked for.
     *
     * @param doc The document we found
     * @param lastModifiedTimestamp The timestamp with which the document was stored.
     */
    public GetResult(Document doc, long lastModifiedTimestamp) {
        this.doc = doc;
        this.lastModifiedTimestamp = lastModifiedTimestamp;
    }

    /**
     * @return Returns the timestamp at which the document was last modified, or 0 if
     * no document was found.
     */
    public long getLastModifiedTimestamp() { return lastModifiedTimestamp;}

    /**
     * @return Returns true if the document was found.
     */
    public boolean wasFound() {
        return doc != null;
    }

    public boolean hasDocument() {
        return doc != null;
    }

    public Document getDocument() {
        return doc;
    }
}
