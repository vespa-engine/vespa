// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc.messagebus;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.docproc.AbstractConcreteDocumentFactory;
import com.yahoo.docproc.DocprocService;
import com.yahoo.docproc.HandledProcessingException;
import com.yahoo.docproc.Processing;
import com.yahoo.docproc.TransientFailureException;
import com.yahoo.docproc.jdisc.RequestContext;
import com.yahoo.document.DocumentOperation;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.jdisc.MbusRequest;
import com.yahoo.messagebus.jdisc.MbusResponse;
import com.yahoo.messagebus.routing.Route;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class MbusRequestContext implements RequestContext, ResponseHandler {

    private final static Logger log = Logger.getLogger(MbusRequestContext.class.getName());
    private final static CopyOnWriteHashMap<String, URI> uriCache = new CopyOnWriteHashMap<>();
    private final AtomicBoolean deserialized = new AtomicBoolean(false);
    private final AtomicBoolean responded = new AtomicBoolean(false);
    private final ProcessingFactory processingFactory;
    private final MessageFactory messageFactory;
    private final MbusRequest request;
    private final DocumentMessage requestMsg;
    private final ResponseHandler responseHandler;
    private volatile int cachedApproxSize;
    // When spawning off new documents inside document processor, we do not want
    // throttling since this can lead to live locks. This is because the
    // document being processed is a resource and is then grabbing more resources of
    // the same type without releasing its own resources.
    public final static String internalNoThrottledSource = "internalNoThrottledSource";

    public MbusRequestContext(MbusRequest request, ResponseHandler responseHandler,
                              ComponentRegistry<DocprocService> docprocServiceComponentRegistry,
                              ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry,
                              ContainerDocumentConfig containerDocConfig) {
        this.request = request;
        this.requestMsg = (DocumentMessage)request.getMessage();
        this.responseHandler = responseHandler;
        this.processingFactory = new ProcessingFactory(docprocServiceComponentRegistry, docFactoryRegistry,
                                                       containerDocConfig, getServiceName());
        this.messageFactory = newMessageFactory(requestMsg);
    }

    @Override
    public List<Processing> getProcessings() {
        if (deserialized.getAndSet(true)) {
            return Collections.emptyList();
        }
        return processingFactory.fromMessage(requestMsg);
    }

    @Override
    public void skip() {
        if (deserialized.get()) {
            throw new IllegalStateException("Can not skip processing after deserialization.");
        }
        dispatchRequest(requestMsg, request.getUri().getPath(), responseHandler);
    }

    @Override
    public void processingDone(List<Processing> processings) {
        List<DocumentMessage> messages = new ArrayList<>();
        if (messageFactory != null) {
            for (Processing processing : processings) {
                for (DocumentOperation documentOperation : processing.getDocumentOperations()) {
                    messages.add(messageFactory.fromDocumentOperation(processing, documentOperation));
                }
            }
        }
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Forwarding " + messages.size() + " messages from " + processings.size() +
                                    " processings.");
        }
        if (messages.isEmpty()) {
            dispatchResponse(Response.Status.OK);
            return;
        }
        long inputSequenceId = requestMsg.getSequenceId();
        ResponseMerger responseHandler = new ResponseMerger(requestMsg, messages.size(), this);
        for (Message message : messages) {
            // See comment for internalNoThrottledSource.
            dispatchRequest(message, (inputSequenceId == message.getSequenceId())
                            ? getUri().getPath()
                            : "/" + internalNoThrottledSource,
                            responseHandler);
        }
    }

    @Override
    public void processingFailed(Exception exception) {
        ErrorCode errorCode;
        if (exception instanceof TransientFailureException) {
            errorCode = ErrorCode.ERROR_ABORTED;
        } else {
            errorCode = ErrorCode.ERROR_PROCESSING_FAILURE;
        }
        StringBuilder errorMsg = new StringBuilder("Processing failed.");
        if (exception instanceof HandledProcessingException) {
            errorMsg.append(" Error message: ").append(exception.getMessage());
        } else if (exception != null) {
            errorMsg.append(" Error message: ").append(exception.toString());
        }
        errorMsg.append(" -- See Vespa log for details.");
        processingFailed(errorCode, errorMsg.toString());
    }

    @Override
    public void processingFailed(ErrorCode errorCode, String errorMsg) {
        MbusResponse response = new MbusResponse(errorCode.getDiscStatus(), requestMsg.createReply());
        response.getReply().addError(new com.yahoo.messagebus.Error(errorCode.getDocumentProtocolStatus(), errorMsg));
        ResponseDispatch.newInstance(response).dispatch(this);
    }

    @Override
    public int getApproxSize() {
        if (cachedApproxSize > 0) {
            return cachedApproxSize;
        }
        cachedApproxSize = requestMsg.getApproxSize();
        return cachedApproxSize;
    }

    @Override
    public int getPriority() {
        return requestMsg.getPriority().getValue();
    }

    @Override
    public URI getUri() {
        return request.getUri();
    }

    @Override
    public String getServiceName() {
        String path = getUri().getPath();
        return path.substring(7, path.length());
    }

    @Override
    public boolean isProcessable() {
        Message msg = requestMsg;
        switch (msg.getType()) {
        case DocumentProtocol.MESSAGE_PUTDOCUMENT:
        case DocumentProtocol.MESSAGE_UPDATEDOCUMENT:
        case DocumentProtocol.MESSAGE_REMOVEDOCUMENT:
        case DocumentProtocol.MESSAGE_BATCHDOCUMENTUPDATE:
            return true;
        }
        return false;
    }

    @Override
    public boolean hasExpired() {
        return requestMsg.isExpired();
    }

    @Override
    public ContentChannel handleResponse(Response response) {
        if (responded.getAndSet(true)) {
            return null;
        }
        Reply reply = ((MbusResponse)response).getReply();
        reply.swapState(requestMsg);
        return responseHandler.handleResponse(response);
    }

    private void dispatchResponse(int status) {
        ResponseDispatch.newInstance(new MbusResponse(status, requestMsg.createReply())).dispatch(this);
    }

    private void dispatchRequest(final Message msg, final String uriPath, final ResponseHandler handler) {
        try {
            new RequestDispatch() {

                @Override
                protected Request newRequest() {
                    return new MbusRequest(request, resolveUri(uriPath), msg);
                }

                @Override
                public ContentChannel handleResponse(Response response) {
                    return handler.handleResponse(response);
                }
            }.dispatch();
        } catch (Exception e) {
            dispatchResponse(Response.Status.INTERNAL_SERVER_ERROR);
            e.printStackTrace();
        }
    }

    private static MessageFactory newMessageFactory(final DocumentMessage msg) {
        if (msg == null) {
            return null;
        }
        final Route route = msg.getRoute();
        if (route == null || !route.hasHops()) {
            return null;
        }
        return new MessageFactory(msg);
    }

    private static URI resolveUri(String path) {
        URI uri = uriCache.get(path);
        if (uri == null) {
            uri = URI.create("mbus://remotehost" + path);
            uriCache.put(path, uri);
        }
        return uri;
    }
}
