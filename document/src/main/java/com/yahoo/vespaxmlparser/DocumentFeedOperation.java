// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.Document;
import com.yahoo.document.TestAndSetCondition;

public class DocumentFeedOperation extends ConditionalFeedOperation {

    private final Document document;

    public DocumentFeedOperation(Document document) {
        super(Type.DOCUMENT);
        this.document = document;
    }

    public DocumentFeedOperation(Document document, TestAndSetCondition condition) {
        super(Type.DOCUMENT, condition);
        this.document = document;
    }

    @Override
    public Document getDocument() {
        return document;
    }

}

