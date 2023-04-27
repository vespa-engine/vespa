// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DocumentPut;
import com.yahoo.document.TestAndSetCondition;

public class DocumentFeedOperation extends FeedOperation {

    private final DocumentPut put;

    public DocumentFeedOperation(DocumentPut put) {
        super(Type.DOCUMENT);
        this.put = put;
    }

    @Override
    public DocumentPut getDocumentPut() {
        return put;
    }

    @Override
    public TestAndSetCondition getCondition() {
        return put.getCondition();
    }

}

