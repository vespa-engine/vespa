// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DocumentPut;

public class DocumentFeedOperation extends ConditionalFeedOperation {

    private final DocumentPut put;

    public DocumentFeedOperation(DocumentPut put) {
        super(Type.DOCUMENT, put.getCondition());
        this.put = put;
    }

    @Override
    public DocumentPut getDocumentPut() {
        return put;
    }

}

