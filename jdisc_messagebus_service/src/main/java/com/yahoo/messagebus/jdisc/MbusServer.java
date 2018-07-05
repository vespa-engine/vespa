// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.google.inject.Inject;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.shared.ServerSession;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public final class MbusServer extends AbstractResource implements ServerProvider, MessageHandler {

    private final static Logger log = Logger.getLogger(MbusServer.class.getName());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CurrentContainer container;
    private final ServerSession session;
    private final URI uri;
    private final ResourceReference sessionReference;

    @Inject
    public MbusServer(final CurrentContainer container, final ServerSession session) {
        this.container = container;
        this.session = session;
        uri = URI.create("mbus://localhost/" + session.name());
        session.setMessageHandler(this);
        sessionReference = session.refer();
    }

    @Override
    public void start() {
        log.log(LogLevel.DEBUG, "Starting message bus server.");
        running.set(true);
    }

    @Override
    public void close() {
        log.log(LogLevel.DEBUG, "Closing message bus server.");
        running.set(false);
    }

    @Override
    protected void destroy() {
        log.log(LogLevel.DEBUG, "Destroying message bus server.");
        running.set(false);
        sessionReference.close();
    }

    @Override
    public void handleMessage(final Message msg) {
        if (!running.get()) {
            dispatchErrorReply(msg, ErrorCode.SESSION_BUSY, "Session temporarily closed.");
            return;
        }
        if (msg.getTrace().shouldTrace(6)) {
            msg.getTrace().trace(6, "Message received by MbusServer.");
        }
        Request request = null;
        ContentChannel content = null;
        try {
            request = new MbusRequest(container, uri, msg);
            content = request.connect(new ServerResponseHandler(msg));
        } catch (final RuntimeException e) {
            dispatchErrorReply(msg, ErrorCode.APP_FATAL_ERROR, e.toString());
        } finally {
            if (request != null) {
                request.release();
            }
        }
        if (content != null) {
            content.close(IgnoredCompletionHandler.INSTANCE);
        }
    }

    public String connectionSpec() {
        return session.connectionSpec();
    }

    private void dispatchErrorReply(final Message msg, final int errCode, final String errMsg) {
        final Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(errCode, errMsg));
        session.sendReply(reply);
    }

    private class ServerResponseHandler implements ResponseHandler {

        final Message msg;

        ServerResponseHandler(final Message msg) {
            this.msg = msg;
        }

        @Override
        public ContentChannel handleResponse(final Response response) {
            final Reply reply;
            if (response instanceof MbusResponse) {
                reply = ((MbusResponse)response).getReply();
            } else {
                reply = new EmptyReply();
                reply.swapState(msg);
            }
            final Error err = StatusCodes.toMbusError(response.getStatus());
            if (err != null) {
                if (err.isFatal()) {
                    if (!reply.hasFatalErrors()) {
                        reply.addError(err);
                    }
                } else {
                    if (!reply.hasErrors()) {
                        reply.addError(err);
                    }
                }
            }
            if (reply.getTrace().shouldTrace(6)) {
                reply.getTrace().trace(6, "Sending reply from MbusServer.");
            }
            session.sendReply(reply);
            return null;
        }
    }
}
