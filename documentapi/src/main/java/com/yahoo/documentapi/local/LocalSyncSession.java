// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.local;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

import java.time.Duration;

/**
 * @author bratseth
 */
public class LocalSyncSession implements SyncSession {

    private LocalDocumentAccess access;

    public LocalSyncSession(LocalDocumentAccess access) {
        this.access = access;
    }

    @Override
    public void put(DocumentPut documentPut) {
        if (documentPut.getCondition().isPresent()) {
            throw new UnsupportedOperationException("test-and-set is not supported.");
        }

        access.documents.put(documentPut.getId(), documentPut.getDocument());
    }

    @Override
    public Document get(DocumentId id, Duration timeout) {
        return access.documents.get(id);
    }

    @Override
    public boolean remove(DocumentRemove documentRemove) {
        if (documentRemove.getCondition().isPresent()) {
            throw new UnsupportedOperationException("test-and-set is not supported.");
        }
        access.documents.remove(documentRemove.getId());
        return true;
    }

    @Override
    public boolean update(DocumentUpdate update) {
        Document document = access.documents.get(update.getId());
        if (document == null) {
            return false;
        }
        update.applyTo(document);
        return true;
    }

    @Override
    public Response getNext() {
        throw new UnsupportedOperationException("Queue not supported.");
    }

    @Override
    public Response getNext(int timeout) {
        throw new UnsupportedOperationException("Queue not supported.");
    }

    @Override
    public void destroy() {
        access = null;
    }

}
