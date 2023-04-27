// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DocumentUpdate;

public class DocumentUpdateFeedOperation extends ConditionalFeedOperation {
    private final DocumentUpdate update;
    public DocumentUpdateFeedOperation(DocumentUpdate update) {
        super(Type.UPDATE, update.getCondition());
        this.update = update;
    }

    @Override
    public DocumentUpdate getDocumentUpdate() {
        return update;
    }
}
