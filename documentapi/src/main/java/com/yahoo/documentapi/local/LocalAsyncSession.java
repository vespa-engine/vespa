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

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.yahoo.documentapi.Result.ResultType.SUCCESS;

/**
 * @author bratseth
 * @author jonmv
 */
public class LocalAsyncSession implements AsyncSession {

    private final BlockingQueue<Response> responses = new LinkedBlockingQueue<>();
    private final ResponseHandler handler;
    private final SyncSession syncSession;

    private AtomicLong requestId = new AtomicLong(0);
    private AtomicReference<Runnable> synchronizer = new AtomicReference<>();
    private AtomicReference<Result.ResultType> result = new AtomicReference<>(SUCCESS);

    public LocalAsyncSession(AsyncParameters params, LocalDocumentAccess access) {
        this.handler = params.getResponseHandler();
        syncSession = access.createSyncSession(new SyncParameters.Builder().build());
    }

    @Override
    public double getCurrentWindowSize() {
        return 1000;
    }

    @Override
    public Result put(Document document) {
        return put(new DocumentPut(document), DocumentProtocol.Priority.NORMAL_3);
    }

    @Override
    public Result put(DocumentPut documentPut, DocumentProtocol.Priority pri) {
        return send(req -> {
            try {
                syncSession.put(documentPut, pri);
                return new DocumentResponse(req, documentPut.getDocument());
            }
            catch (Exception e) {
                return new DocumentResponse(req, documentPut.getDocument(), e.getMessage(), Response.Outcome.ERROR);
            }
        });
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
        return send(req -> {
            try {
                return new DocumentResponse(req, syncSession.get(id));
            }
            catch (Exception e) {
                return new DocumentResponse(req, null, e.getMessage(), Response.Outcome.ERROR);
            }
        });
    }

    @Override
    public Result remove(DocumentId id) {
        return remove(id, DocumentProtocol.Priority.NORMAL_3);
    }

    @Override
    public Result remove(DocumentId id, DocumentProtocol.Priority pri) {
        return send(req -> {
            if (syncSession.remove(new DocumentRemove(id), pri)) {
                return new RemoveResponse(req, true);
            }
            else {
                return new DocumentIdResponse(req, id, "Document not found.", Response.Outcome.NOT_FOUND);
            }
        });
    }

    @Override
    public Result update(DocumentUpdate update) {
        return update(update, DocumentProtocol.Priority.NORMAL_3);
    }

    @Override
    public Result update(DocumentUpdate update, DocumentProtocol.Priority pri) {
        return send(req -> {
            if (syncSession.update(update, pri)) {
                return new UpdateResponse(req, true);
            }
            else {
                return new DocumentUpdateResponse(req, update, "Document not found.", Response.Outcome.NOT_FOUND);
            }
        });
    }

    @Override
    public Response getNext() {
        return responses.poll();
    }

    @Override
    public Response getNext(int timeoutMilliseconds) throws InterruptedException {
        return responses.poll(timeoutMilliseconds, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        // empty
    }

    /**
     * This is run in a separate thread before providing the response from each accepted request, for advanced testing.
     *
     * If this is not set, which is the default, the documents appear synchronously in the response queue or handler.
     */
    public void setSynchronizer(Runnable synchronizer) {
        this.synchronizer.set(synchronizer);
    }

    /** Sets the result type returned on subsequence operations against this. Only SUCCESS will cause Repsonses to appear. */
    public void setResultType(Result.ResultType resultType) {
        this.result.set(resultType);
    }

    private void addResponse(Response response) {
        if (handler != null) {
            handler.handleResponse(response);
        } else {
            responses.add(response);
        }
    }

    private Result send(Function<Long, Response> responses) {
        Result.ResultType resultType = result.get();
        if (resultType != SUCCESS)
            return new Result(resultType, new Error());

        long req = requestId.incrementAndGet();
        synchronizer.getAndUpdate(runnable -> {
            if (runnable == null)
                addResponse(responses.apply(req));
            else
                new Thread(() -> {
                    runnable.run();
                    addResponse(responses.apply(req));
                }).start();
            return runnable;
        });
        return new Result(req);
    }

}
