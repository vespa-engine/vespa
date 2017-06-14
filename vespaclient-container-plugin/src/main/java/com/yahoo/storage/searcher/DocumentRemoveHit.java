// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.document.DocumentId;
import com.yahoo.search.result.Hit;

public class DocumentRemoveHit extends Hit {

    private final DocumentId idOfRemovedDoc;

    public DocumentRemoveHit(DocumentId idOfRemovedDoc) {
        super(idOfRemovedDoc.toString());
        this.idOfRemovedDoc = idOfRemovedDoc;
        setField("documentid", idOfRemovedDoc.toString());
    }

    public DocumentId getIdOfRemovedDoc() {
        return idOfRemovedDoc;
    }

}
