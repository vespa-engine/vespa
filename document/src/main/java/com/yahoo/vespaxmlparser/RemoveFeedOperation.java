// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.TestAndSetCondition;

public class RemoveFeedOperation extends ConditionalFeedOperation {
    private final DocumentRemove remove;
    public RemoveFeedOperation(DocumentRemove remove) {
        super(Type.REMOVE, remove.getCondition());
        this.remove = remove;
    }

    @Override
    public DocumentRemove getDocumentRemove() {
        return remove;
    }
}
