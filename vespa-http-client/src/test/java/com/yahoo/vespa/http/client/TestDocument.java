// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import net.jcip.annotations.Immutable;

/**
* @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
* @since 5.1.20
*/
@Immutable
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
