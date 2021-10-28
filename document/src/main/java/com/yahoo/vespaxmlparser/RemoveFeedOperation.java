// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DocumentId;
import com.yahoo.document.TestAndSetCondition;

public class RemoveFeedOperation extends ConditionalFeedOperation {
    private final DocumentId documentId;
    public RemoveFeedOperation(DocumentId documentId) {
        super(Type.REMOVE);
        this.documentId = documentId;
    }
    public RemoveFeedOperation(DocumentId documentId, TestAndSetCondition condition) {
        super(Type.REMOVE, condition);
        this.documentId = documentId;
    }

    @Override
    public DocumentId getRemove() {
        return documentId;
    }
}
