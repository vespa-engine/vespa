// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

/**
* @author Einar M R Rosenvinge
*/
public class TestDocument {
    private final String documentId;
    private final byte[] contents;

    TestDocument(String documentId, byte[] contents) {
        this.documentId = documentId;
        this.contents = contents;
    }

    public String getDocumentId() {
        return documentId;
    }

    public byte[] getContents() {
        return contents;
    }
}
