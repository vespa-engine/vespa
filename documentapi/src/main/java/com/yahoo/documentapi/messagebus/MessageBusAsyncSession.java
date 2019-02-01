// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
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
import com.yahoo.documentapi.UpdateResponse;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentReply;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentReply;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentReply;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.ThrottlePolicy;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * An access session which wraps a messagebus source session sending document messages.
 * The sessions are multithread safe.
 *
 * @author bratseth
 * @author Einar Rosenvinge
 */
public class MessageBusAsyncSession implements MessageBusSession, AsyncSession {

    private static final Logger log = Logger.getLogger(MessageBusAsyncSession.class.getName());
    private final AtomicLong requestId = new AtomicLong(0);
    private final BlockingQueue<Response> responses = new LinkedBlockingQueue<>();
    private final ThrottlePolicy throttlePolicy;
    private final SourceSession session;
    private String route;
    private String routeForGet;
    private int traceLevel;

    /**
     * Creates a new async session running on message bus logic.
     *
     * @param asyncParams Common asyncsession parameters, not used.
     * @param bus         The message bus on which to run.
     * @param mbusParams  Parameters concerning message bus configuration.
     */
    MessageBusAsyncSession(AsyncParameters asyncParams, MessageBus bus, MessageBusParams mbusParams) {
        this(asyncParams, bus, mbusParams, null);
    }

    /**
     * Creates a new async session running on message bus logic with a specified reply handler.
     *
     * @param asyncParams Common asyncsession parameters, not used.
     * @param bus         The message bus on which to run.
     * @param mbusParams  Parameters concerning message bus configuration.
     * @param handler     The external reply handler.
     */
    MessageBusAsyncSession(AsyncParameters asyncParams, MessageBus bus, MessageBusParams mbusParams,
                           ReplyHandler handler) {
        route = mbusParams.getRoute();
        routeForGet = mbusParams.getRouteForGet();
        traceLevel = mbusParams.getTraceLevel();
        throttlePolicy = mbusParams.getSourceSessionParams().getThrottlePolicy();
        if (handler == null) {
            handler = new MyReplyHandler(asyncParams.getResponseHandler(), responses);
        }
        session = bus.createSourceSession(handler, mbusParams.getSourceSessionParams());
    }

    @Override
    public Result put(Document document) {
        return put(document, DocumentProtocol.Priority.NORMAL_3);
    }

    @Override
    public Result put(Document document, DocumentProtocol.Priority pri) {
        PutDocumentMessage msg = new PutDocumentMessage(new DocumentPut(document));
        msg.setPriority(pri);
        return send(msg);
    }

    @Override
    public Result get(DocumentId id) {
        return get(id, DocumentProtocol.Priority.NORMAL_1);
    }

    @Override
    @Deprecated // TODO: Remove on Vespa 8
    public Result get(DocumentId id, boolean headersOnly, DocumentProtocol.Priority pri) {
        return get(id, pri);
    }

    @Override
    public Result get(DocumentId id, DocumentProtocol.Priority pri) {
        GetDocumentMessage msg = new GetDocumentMessage(id, "[all]");
        msg.setPriority(pri);
        return send(msg);
    }

    @Override
    public Result remove(DocumentId id) {
        return remove(id, DocumentProtocol.Priority.NORMAL_2);
    }

    @Override
    public Result remove(DocumentId id, DocumentProtocol.Priority pri) {
        RemoveDocumentMessage msg = new RemoveDocumentMessage(id);
        msg.setPriority(pri);
        return send(msg);
    }

    @Override
    public Result update(DocumentUpdate update) {
        return update(update, DocumentProtocol.Priority.NORMAL_2);
    }

    @Override
    public Result update(DocumentUpdate update, DocumentProtocol.Priority pri) {
        UpdateDocumentMessage msg = new UpdateDocumentMessage(update);
        msg.setPriority(pri);
        return send(msg);
    }

    /**
     * A convenience method for assigning the internal trace level and route string to a message before sending it
     * through the internal mbus session object.
     *
     * @param msg the message to send.
     * @return the document api result object.
     */
    public Result send(Message msg) {
        try {
            long reqId = requestId.incrementAndGet();
            msg.setContext(reqId);
            msg.getTrace().setLevel(traceLevel);
            String toRoute = (msg.getType() == DocumentProtocol.MESSAGE_GETDOCUMENT ? routeForGet : route);
            if (toRoute != null) {
                return toResult(reqId, session.send(msg, toRoute, true));
            } else {
                return toResult(reqId, session.send(msg));
            }
        } catch (Exception e) {
            return new Result(Result.ResultType.FATAL_ERROR, new Error(e.getMessage(), e));
        }
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
        session.destroy();
    }

    @Override
    public String getRoute() {
        return route;
    }

    @Override
    public void setRoute(String route) {
        this.route = route;
    }

    @Override
    public int getTraceLevel() {
        return traceLevel;
    }

    @Override
    public void setTraceLevel(int traceLevel) {
        this.traceLevel = traceLevel;
    }

    @Override
    public double getCurrentWindowSize() {
        if (throttlePolicy instanceof StaticThrottlePolicy) {
            return ((StaticThrottlePolicy)throttlePolicy).getMaxPendingCount();
        }
        return 0;
    }

    /**
     * Returns a concatenated error string from the errors contained in a reply.
     *
     * @param reply The reply whose errors to concatenate.
     * @return The error string.
     */
    static String getErrorMessage(Reply reply) {
        if (!reply.hasErrors()) {
            return null;
        }
        StringBuilder errors = new StringBuilder();
        for (int i = 0; i < reply.getNumErrors(); ++i) {
            errors.append(reply.getError(i)).append(" ");
        }
        return errors.toString();
    }

    private static Result.ResultType messageBusErrorToResultType(int messageBusError) {
        switch (messageBusError) {
            case ErrorCode.SEND_QUEUE_FULL: return Result.ResultType.TRANSIENT_ERROR;
            case DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED: return Result.ResultType.CONDITION_NOT_MET_ERROR;
            default: return Result.ResultType.FATAL_ERROR;
        }
    }

    private static Result toResult(long reqId, com.yahoo.messagebus.Result mbusResult) {
        if (mbusResult.isAccepted()) {
            return new Result(reqId);
        }
        return new Result(
                messageBusErrorToResultType(mbusResult.getError().getCode()),
                new Error(mbusResult.getError().getMessage() + " (" + mbusResult.getError().getCode() + ")"));
    }

    private static Response toResponse(Reply reply) {
        long reqId = (Long)reply.getContext();
        return reply.hasErrors() ? toError(reply, reqId) : toSuccess(reply, reqId);
    }

    private static Response toError(Reply reply, long reqId) {
        Message msg = reply.getMessage();
        String err = getErrorMessage(reply);
        switch (msg.getType()) {
        case DocumentProtocol.MESSAGE_PUTDOCUMENT:
            return new DocumentResponse(reqId, ((PutDocumentMessage)msg).getDocumentPut().getDocument(), err, false);
        case DocumentProtocol.MESSAGE_UPDATEDOCUMENT:
            return new DocumentUpdateResponse(reqId, ((UpdateDocumentMessage)msg).getDocumentUpdate(), err, false);
        case DocumentProtocol.MESSAGE_REMOVEDOCUMENT:
            return new DocumentIdResponse(reqId, ((RemoveDocumentMessage)msg).getDocumentId(), err, false);
        case DocumentProtocol.MESSAGE_GETDOCUMENT:
            return new DocumentIdResponse(reqId, ((GetDocumentMessage)msg).getDocumentId(), err, false);
        default:
            return new Response(reqId, err, false);
        }
    }

    private static Response toSuccess(Reply reply, long reqId) {
        switch (reply.getType()) {
            case DocumentProtocol.REPLY_GETDOCUMENT:
                GetDocumentReply docReply = ((GetDocumentReply) reply);
                Document getDoc = docReply.getDocument();
                if (getDoc != null) {
                    getDoc.setLastModified(docReply.getLastModified());
                }
                return new DocumentResponse(reqId, getDoc);
            case DocumentProtocol.REPLY_REMOVEDOCUMENT:
                return new RemoveResponse(reqId, ((RemoveDocumentReply)reply).wasFound());
            case DocumentProtocol.REPLY_UPDATEDOCUMENT:
                return new UpdateResponse(reqId, ((UpdateDocumentReply)reply).wasFound());
            case DocumentProtocol.REPLY_PUTDOCUMENT:
                break;
            default:
                return new Response(reqId);
        }
        Message msg = reply.getMessage();
        switch (msg.getType()) {
            case DocumentProtocol.MESSAGE_PUTDOCUMENT:
                return new DocumentResponse(reqId, ((PutDocumentMessage)msg).getDocumentPut().getDocument());
            case DocumentProtocol.MESSAGE_REMOVEDOCUMENT:
                return new DocumentIdResponse(reqId, ((RemoveDocumentMessage)msg).getDocumentId());
            case DocumentProtocol.MESSAGE_UPDATEDOCUMENT:
                return new DocumentUpdateResponse(reqId, ((UpdateDocumentMessage)msg).getDocumentUpdate());
            default:
                return new Response(reqId);
        }
    }

    private static class MyReplyHandler implements ReplyHandler {

        final ResponseHandler handler;
        final Queue<Response> queue;

        MyReplyHandler(ResponseHandler handler, Queue<Response> queue) {
            this.handler = handler;
            this.queue = queue;
        }

        @Override
        public void handleReply(Reply reply) {
            if (reply.getTrace().getLevel() > 0) {
                log.log(LogLevel.INFO, reply.getTrace().toString());
            }
            Response response = toResponse(reply);
            if (handler != null) {
                handler.handleResponse(response);
            } else {
                queue.add(response);
            }
        }
    }

}
