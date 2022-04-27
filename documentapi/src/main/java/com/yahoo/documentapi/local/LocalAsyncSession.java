// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.local;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentIdResponse;
import com.yahoo.documentapi.DocumentOperationParameters;
import com.yahoo.documentapi.DocumentResponse;
import com.yahoo.documentapi.DocumentUpdateResponse;
import com.yahoo.documentapi.RemoveResponse;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.ResponseHandler;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.UpdateResponse;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.yahoo.documentapi.DocumentOperationParameters.parameters;
import static com.yahoo.documentapi.Result.ResultType.SUCCESS;

/**
 * @author bratseth
 * @author jonmv
 */
public class LocalAsyncSession implements AsyncSession {

    private final BlockingQueue<Response> responses = new LinkedBlockingQueue<>();
    private final ResponseHandler handler;
    private final SyncSession syncSession;
    private final Executor executor = Executors.newCachedThreadPool();
    private final AtomicReference<Phaser> phaser;

    private final AtomicLong requestId = new AtomicLong(0);
    private final AtomicReference<Result.ResultType> result = new AtomicReference<>(SUCCESS);

    public LocalAsyncSession(AsyncParameters params, LocalDocumentAccess access) {
        this.handler = params.getResponseHandler();
        this.syncSession = access.createSyncSession(new SyncParameters.Builder().build());
        this.phaser = access.phaser;
    }

    @Override
    public double getCurrentWindowSize() {
        return 1000;
    }

    @Override
    public Result put(Document document) {
        return put(new DocumentPut(document), parameters());
    }

    @Override
    public Result put(DocumentPut documentPut, DocumentOperationParameters parameters) {
        return send(req -> {
                        try {
                            syncSession.put(documentPut, parameters);
                            return new DocumentResponse(req, documentPut.getDocument());
                        }
                        catch (Exception e) {
                            return new DocumentResponse(req, documentPut.getDocument(), e.getMessage(), Response.Outcome.ERROR);
                        }
                    },
                    parameters);
    }

    @Override
    public Result get(DocumentId id) {
        return get(id, parameters());
    }

    @Override
    public Result get(DocumentId id, DocumentOperationParameters parameters) {
        return send(req -> {
                        try {
                            return new DocumentResponse(req, syncSession.get(id, parameters, null));
                        }
                        catch (Exception e) {
                            return new DocumentResponse(req, null, e.getMessage(), Response.Outcome.ERROR);
                        }
                    },
                    parameters);
    }

    @Override
    public Result remove(DocumentId id) {
        return remove(id, parameters());
    }

    @Override
    public Result remove(DocumentRemove remove, DocumentOperationParameters parameters) {
        return send(req -> {
                        if (syncSession.remove(remove, parameters)) {
                            return new RemoveResponse(req, true);
                        }
                        else {
                            return new DocumentIdResponse(req, remove.getId(), "Document not found.", Response.Outcome.NOT_FOUND);
                        }
                    },
                    parameters);
    }

    @Override
    public Result update(DocumentUpdate update) {
        return update(update, parameters());
    }

    @Override
    public Result update(DocumentUpdate update, DocumentOperationParameters parameters) {
        return send(req -> {
                        if (syncSession.update(update, parameters)) {
                            return new UpdateResponse(req, true);
                        }
                        else {
                            return new DocumentUpdateResponse(req, update, "Document not found.", Response.Outcome.NOT_FOUND);
                        }
                    },
                    parameters);
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

    /** Sets the result type returned on subsequence operations against this. Only SUCCESS will cause Responses to appear. */
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

    private Result send(Function<Long, Response> responses, DocumentOperationParameters parameters) {
        Result.ResultType resultType = result.get();
        if (resultType != SUCCESS)
            return new Result(resultType, Result.toError(resultType));

        ResponseHandler responseHandler = parameters.responseHandler().orElse(this::addResponse);
        long req = requestId.incrementAndGet();
        Phaser synchronizer = phaser.get();
        if (synchronizer == null)
            responseHandler.handleResponse(responses.apply(req));
        else {
            synchronizer.register();
            executor.execute(() -> {
                try {
                    synchronizer.arriveAndAwaitAdvance();
                    responseHandler.handleResponse(responses.apply(req));
                }
                finally {
                    synchronizer.awaitAdvance(synchronizer.arriveAndDeregister());
                }
            });
        }
        return new Result(req);
    }

}
