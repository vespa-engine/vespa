// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.fieldset.DocumentOnly;
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
import com.yahoo.documentapi.UpdateResponse;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentReply;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentReply;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.ThrottlePolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.documentapi.DocumentOperationParameters.parameters;
import static com.yahoo.documentapi.Response.Outcome.CONDITION_FAILED;
import static com.yahoo.documentapi.Response.Outcome.ERROR;
import static com.yahoo.documentapi.Response.Outcome.INSUFFICIENT_STORAGE;
import static com.yahoo.documentapi.Response.Outcome.NOT_FOUND;
import static com.yahoo.documentapi.Response.Outcome.OVERLOAD;
import static com.yahoo.documentapi.Response.Outcome.REJECTED;
import static com.yahoo.documentapi.Response.Outcome.SUCCESS;
import static com.yahoo.documentapi.Response.Outcome.TIMEOUT;

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
    private final SourceSession session;
    private final String routeForGet;
    private String route;
    private int traceLevel;

    /**
     * Creates a new async session running on message bus logic.
     *
     * @param asyncParams common asyncsession parameters, not used
     * @param bus         the message bus on which to run
     * @param mbusParams  parameters concerning message bus configuration
     */
    MessageBusAsyncSession(AsyncParameters asyncParams, MessageBus bus, MessageBusParams mbusParams) {
        this(asyncParams, bus, mbusParams, null);
    }

    /**
     * Creates a new async session running on message bus logic with a specified reply handler.
     *
     * @param asyncParams common asyncsession parameters, not used
     * @param bus         the message bus on which to run
     * @param mbusParams  parameters concerning message bus configuration
     * @param handler     the external reply handler
     */
    MessageBusAsyncSession(AsyncParameters asyncParams, MessageBus bus, MessageBusParams mbusParams,
                           ReplyHandler handler) {
        route = mbusParams.getRoute();
        routeForGet = mbusParams.getRouteForGet();
        traceLevel = mbusParams.getTraceLevel();
        SourceSessionParams sourceSessionParams = new SourceSessionParams(mbusParams.getSourceSessionParams());
        if (asyncParams.getThrottlePolicy() != null) {
            sourceSessionParams.setThrottlePolicy(asyncParams.getThrottlePolicy());
        }
        sourceSessionParams.setReplyHandler((handler != null) ? handler : new MyReplyHandler(asyncParams.getResponseHandler(), responses));
        session = bus.createSourceSession(sourceSessionParams);
    }

    @Override
    public Result put(Document document) {
        return put(new DocumentPut(document), parameters());
    }

    @Override
    public Result put(DocumentPut documentPut, DocumentOperationParameters parameters) {
        PutDocumentMessage msg = new PutDocumentMessage(documentPut);
        return send(msg, parameters);
    }

    @Override
    public Result get(DocumentId id) {
        return get(id, parameters());
    }

    @Override
    public Result get(DocumentId id, DocumentOperationParameters parameters) {
        GetDocumentMessage msg = new GetDocumentMessage(id, parameters.fieldSet().orElse(DocumentOnly.NAME));
        return send(msg, parameters);
    }

    @Override
    public Result remove(DocumentId id) {
        return remove(new DocumentRemove(id), parameters());
    }

    @Override
    public Result remove(DocumentRemove remove, DocumentOperationParameters parameters) {
        RemoveDocumentMessage msg = new RemoveDocumentMessage(remove);
        return send(msg, parameters);
    }

    @Override
    public Result update(DocumentUpdate update) {
        return update(update, parameters());
    }

    @Override
    public Result update(DocumentUpdate update, DocumentOperationParameters parameters) {
        UpdateDocumentMessage msg = new UpdateDocumentMessage(update);
        return send(msg, parameters);
    }

    // TODO jonmv: Was this done to remedy get route no longer being possible to set through doc/v1 after default-get was added?
    // TODO jonmv: If so, this is no longer needed with doc/v1.1 and later.
    private boolean mayOverrideWithGetOnlyRoute(Message msg) {
        // Only allow implicitly overriding the default Get route if the message is attempted sent
        // with the default route originally. Otherwise it's reasonable to assume that the caller
        // has some explicit idea of why the regular route is set to the value it is.
        return ((msg.getType() == DocumentProtocol.MESSAGE_GETDOCUMENT)
                && ("default".equals(route) || "route:default".equals(route)));
    }

    Result send(Message msg, DocumentOperationParameters parameters) {
        try {
            long reqId = requestId.incrementAndGet();
            msg.setContext(new OperationContext(reqId, parameters.responseHandler().orElse(null)));
            msg.getTrace().setLevel(parameters.traceLevel().orElse(traceLevel));
            parameters.deadline().ifPresent(deadline -> msg.setTimeRemaining(Math.max(1, Duration.between(Instant.now(), deadline).toMillis())));
            // Use route from parameters, or session route if non-default, or finally, defaults for get and non-get, if set. Phew!
            String toRoute = parameters.route().orElse(mayOverrideWithGetOnlyRoute(msg) ? routeForGet : route);
            if (toRoute != null) {
                return toResult(reqId, session.send(msg, toRoute, true));
            } else {
                return toResult(reqId, session.send(msg));
            }
        } catch (Exception e) {
            return new Result(Result.ResultType.FATAL_ERROR, new Error(ErrorCode.FATAL_ERROR, e.toString()));
        }
    }

    private static class OperationContext {
        private final long reqId;
        private final ResponseHandler responseHandler;
        private OperationContext(long reqId, ResponseHandler responseHandler) {
            this.reqId = reqId;
            this.responseHandler = responseHandler;
        }
    }

    /**
     * A convenience method for assigning the internal trace level and route string to a message before sending it
     * through the internal mbus session object.
     *
     * @param msg the message to send.
     * @return the document api result object.
     */
    public Result send(Message msg) {
        return send(msg, parameters());
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
        if (getThrottlePolicy() instanceof StaticThrottlePolicy) {
            return ((StaticThrottlePolicy)getThrottlePolicy()).getMaxPendingCount();
        }
        return 0;
    }

    ThrottlePolicy getThrottlePolicy() { return session.getThrottlePolicy(); }

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
            default: return Result.ResultType.FATAL_ERROR;
        }
    }

    private static Result toResult(long reqId, com.yahoo.messagebus.Result mbusResult) {
        if (mbusResult.isAccepted()) {
            return new Result(reqId);
        }
        return new Result(
                messageBusErrorToResultType(mbusResult.getError().getCode()), mbusResult.getError());
    }

    private static Response.Outcome toOutcome(Reply reply) {
        if (reply.getErrorCodes().contains(DocumentProtocol.ERROR_OVERLOAD)) {
            return OVERLOAD;
        }
        if (reply.getErrorCodes().contains(DocumentProtocol.ERROR_NO_SPACE)) {
            return INSUFFICIENT_STORAGE;
        }
        if (reply.getErrorCodes().contains(DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED)) {
            return CONDITION_FAILED;
        }
        if (   reply instanceof UpdateDocumentReply && ! ((UpdateDocumentReply) reply).wasFound()
            || reply instanceof RemoveDocumentReply && ! ((RemoveDocumentReply) reply).wasFound()
            || reply.getErrorCodes().contains(DocumentProtocol.ERROR_DOCUMENT_NOT_FOUND)) {
            return NOT_FOUND;
        }
        if (reply.getErrorCodes().contains(DocumentProtocol.ERROR_REJECTED)) {
            return REJECTED;
        }
        if (reply.getErrorCodes().contains(ErrorCode.TIMEOUT)) {
            return TIMEOUT;
        }
        return ERROR;
    }

    private static Response toError(Reply reply, long reqId) {
        Message msg = reply.getMessage();
        String err = getErrorMessage(reply);
        Response.Outcome outcome = toOutcome(reply);
        return switch (msg.getType()) {
            case DocumentProtocol.MESSAGE_PUTDOCUMENT ->
                    new DocumentResponse(reqId, ((PutDocumentMessage) msg).getDocumentPut().getDocument(), err, outcome, reply.getTrace());
            case DocumentProtocol.MESSAGE_UPDATEDOCUMENT ->
                    new DocumentUpdateResponse(reqId, ((UpdateDocumentMessage) msg).getDocumentUpdate(), err, outcome, reply.getTrace());
            case DocumentProtocol.MESSAGE_REMOVEDOCUMENT ->
                    new DocumentIdResponse(reqId, ((RemoveDocumentMessage) msg).getDocumentId(), err, outcome, reply.getTrace());
            case DocumentProtocol.MESSAGE_GETDOCUMENT ->
                    new DocumentIdResponse(reqId, ((GetDocumentMessage) msg).getDocumentId(), err, outcome, reply.getTrace());
            default -> new Response(reqId, err, outcome, reply.getTrace());
        };
    }

    private static Response toSuccess(Reply reply, long reqId) {
        switch (reply.getType()) {
            case DocumentProtocol.REPLY_GETDOCUMENT:
                GetDocumentReply docReply = ((GetDocumentReply) reply);
                Document getDoc = docReply.getDocument();
                if (getDoc != null) {
                    getDoc.setLastModified(docReply.getLastModified());
                }
                return new DocumentResponse(reqId, getDoc, reply.getTrace());
            case DocumentProtocol.REPLY_REMOVEDOCUMENT:
                return new RemoveResponse(reqId, ((RemoveDocumentReply)reply).wasFound(), reply.getTrace());
            case DocumentProtocol.REPLY_UPDATEDOCUMENT:
                return new UpdateResponse(reqId, ((UpdateDocumentReply)reply).wasFound(), reply.getTrace());
            case DocumentProtocol.REPLY_PUTDOCUMENT:
                return new DocumentResponse(reqId, ((PutDocumentMessage)reply.getMessage()).getDocumentPut().getDocument(), reply.getTrace());
            default:
                return new Response(reqId, null, SUCCESS, reply.getTrace());
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
                log.log(Level.FINE, () -> reply.getTrace().toString());
            }
            OperationContext context = (OperationContext) reply.getContext();
            long reqId = context.reqId;
            Response response = reply.hasErrors() ? toError(reply, reqId) : toSuccess(reply, reqId);
            ResponseHandler operationSpecificResponseHandler = context.responseHandler;
            if (operationSpecificResponseHandler != null)
                operationSpecificResponseHandler.handleResponse(response);
            else if (handler != null) {
                handler.handleResponse(response);
            } else {
                queue.add(response);
            }
        }
    }

}
