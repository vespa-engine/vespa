// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.local;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.documentapi.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The main class of the local implementation of the document api
 *
 * @author bratseth
 */
public class LocalDocumentAccess extends DocumentAccess {

    Map<DocumentId, Document> documents = new LinkedHashMap<DocumentId, Document>();

    public LocalDocumentAccess(DocumentAccessParams params) {
        super(params);
    }

    @Override
    public SyncSession createSyncSession(SyncParameters parameters) {
        return new LocalSyncSession(this);
    }

    @Override
    public AsyncSession createAsyncSession(AsyncParameters parameters) {
        return new LocalAsyncSession(parameters, this);
    }

    @Override
    public VisitorSession createVisitorSession(VisitorParameters parameters) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public VisitorDestinationSession createVisitorDestinationSession(VisitorDestinationParameters parameters) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public SubscriptionSession createSubscription(SubscriptionParameters parameters) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public SubscriptionSession openSubscription(SubscriptionParameters parameters) {
        throw new UnsupportedOperationException("Not supported yet");
    }

}
