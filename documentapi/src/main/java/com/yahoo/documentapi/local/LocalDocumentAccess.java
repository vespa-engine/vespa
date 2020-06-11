// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.local;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.SubscriptionParameters;
import com.yahoo.documentapi.SubscriptionSession;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.VisitorDestinationParameters;
import com.yahoo.documentapi.VisitorDestinationSession;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main class of the local implementation of the document api
 *
 * @author bratseth
 */
public class LocalDocumentAccess extends DocumentAccess {

    Map<DocumentId, Document> documents = new ConcurrentHashMap<>();

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
    public VisitorSession createVisitorSession(VisitorParameters parameters) throws ParseException {
        return new LocalVisitorSession(this, parameters);
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
