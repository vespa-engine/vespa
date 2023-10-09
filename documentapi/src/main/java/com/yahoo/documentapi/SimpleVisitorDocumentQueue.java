// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;

import java.util.LinkedList;
import java.util.List;

/**
 * A simple document queue that queues up all results and automatically acks
 * them.
 * <p>
 * Retrieving the list is not thread safe, so wait until visitor is done. This
 * is a simple class merely meant for testing.
 *
 * @author Håkon Humberset
 */
public class SimpleVisitorDocumentQueue extends DumpVisitorDataHandler {
    private final List<Document> documents = new LinkedList<>();

    public void reset() {
        super.reset();
        documents.clear();
    }

    @Override
    public void onDocument(Document doc, long timestamp) {
        documents.add(doc);
    }

    public void onRemove(DocumentId docId) {}

    /** @return a list of all documents retrieved so far */
    public List<Document> getDocuments() {
        return documents;
    }

}
