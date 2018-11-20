// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.local;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentIdResponse;
import com.yahoo.documentapi.DocumentResponse;
import com.yahoo.documentapi.DocumentUpdateResponse;
import com.yahoo.documentapi.RemoveResponse;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.ResponseHandler;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.UpdateResponse;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * @author bratseth
 */
public class LocalAsyncSession implements AsyncSession {

    private final List<Response> responses = new LinkedList<>();
    private final ResponseHandler handler;
    private final SyncSession syncSession;
    private long requestId = 0;
    private Random random = new Random();

    private synchronized long getNextRequestId() {
        requestId++;
        return requestId;
    }

    public LocalAsyncSession(AsyncParameters params, LocalDocumentAccess access) {
        this.handler = params.getResponseHandler();
        random.setSeed(System.currentTimeMillis());
        syncSession = access.createSyncSession(new SyncParameters.Builder().build());
    }

    @Override
    public double getCurrentWindowSize() {
        return 1000;
    }

    @Override
    public Result put(Document document) {
        return put(document, DocumentProtocol.Priority.NORMAL_3);
    }

    @Override
    public Result put(Document document, DocumentProtocol.Priority pri) {
        long req = getNextRequestId();
        try {
            syncSession.put(new DocumentPut(document), pri);
            addResponse(new DocumentResponse(req));
        } catch (Exception e) {
            addResponse(new DocumentResponse(req, document, e.getMessage(), false));
        }
        return new Result(req);
    }

    @Override
    public Result get(DocumentId id) {
        return get(id, false, DocumentProtocol.Priority.NORMAL_3);
    }

    @Override
    @Deprecated // TODO: Remove on Vespa 8
    public Result get(DocumentId id, boolean headersOnly, DocumentProtocol.Priority pri) {
        return get(id, pri);
    }

    @Override
    public Result get(DocumentId id, DocumentProtocol.Priority pri) {
        long req = getNextRequestId();
        try {
            addResponse(new DocumentResponse(req, syncSession.get(id)));
        } catch (Exception e) {
            addResponse(new DocumentResponse(req, e.getMessage(), false));
        }
        return new Result(req);
    }

    @Override
    public Result remove(DocumentId id) {
        return remove(id, DocumentProtocol.Priority.NORMAL_3);
    }

    @Override
    public Result remove(DocumentId id, DocumentProtocol.Priority pri) {
        long req = getNextRequestId();
        if (syncSession.remove(new DocumentRemove(id), pri)) {
            addResponse(new RemoveResponse(req, true));
        } else {
            addResponse(new DocumentIdResponse(req, id, "Document not found.", false));
        }
        return new Result(req);
    }

    @Override
    public Result update(DocumentUpdate update) {
        return update(update, DocumentProtocol.Priority.NORMAL_3);
    }

    @Override
    public Result update(DocumentUpdate update, DocumentProtocol.Priority pri) {
        long req = getNextRequestId();
        if (syncSession.update(update, pri)) {
            addResponse(new UpdateResponse(req, true));
        } else {
            addResponse(new DocumentUpdateResponse(req, update, "Document not found.", false));
        }
        return new Result(req);
    }

    @Override
    public Response getNext() {
        if (responses.isEmpty()) {
            return null;
        }
        int index = random.nextInt(responses.size());
        return responses.remove(index);
    }

    @Override
    public Response getNext(int timeout) {
        return getNext();
    }

    @Override
    public void destroy() {
        // empty
    }

    private void addResponse(Response response) {
        if (handler != null) {
            handler.handleResponse(response);
        } else {
            responses.add(response);
        }
    }

}
