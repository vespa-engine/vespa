// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.shared.ServerSession;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public final class MbusServer extends AbstractResource implements ServerProvider, MessageHandler {

    private enum State {INITIALIZING, RUNNING, STOPPED}
    private final static Logger log = Logger.getLogger(MbusServer.class.getName());
    private final AtomicReference<State> runState = new AtomicReference<>(State.INITIALIZING);
    private final CurrentContainer container;
    private final ServerSession session;
    private final URI uri;
    private final ResourceReference sessionReference;

    @Inject
    public MbusServer(CurrentContainer container, ServerSession session) {
        this.container = container;
        this.session = session;
        uri = URI.create("mbus://localhost/" + session.name());
        session.setMessageHandler(this);
        sessionReference = session.refer(this);
    }

    @Override
    public void start() {
        log.log(Level.FINE, "Starting message bus server.");
        session.connect();
        runState.set(State.RUNNING);
    }

    @Override
    public void close() {
        log.log(Level.FINE, "Closing message bus server.");
        session.disconnect();
        runState.set(State.STOPPED);
    }

    @Override
    public boolean isMultiplexed() {
        return true;
    }

    @Override
    protected void destroy() {
        log.log(Level.FINE, "Destroying message bus server.");
        runState.set(State.STOPPED);
        sessionReference.close();
    }

    @Override
    public void handleMessage(Message msg) {
        State state = runState.get();
        if (state == State.INITIALIZING) {
            dispatchErrorReply(msg, ErrorCode.SESSION_BUSY, "MBusServer not started.");
            return;
        }
        if (state == State.STOPPED) {
            dispatchErrorReply(msg, ErrorCode.NETWORK_SHUTDOWN, "MBusServer has been closed.");
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
        } catch (RuntimeException e) {
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

    private void dispatchErrorReply(Message msg, int errCode, String errMsg) {
        Reply reply = new EmptyReply();
        reply.swapState(msg);
        reply.addError(new Error(errCode, errMsg));
        session.sendReply(reply);
    }

    private class ServerResponseHandler implements ResponseHandler {

        final Message msg;

        ServerResponseHandler(Message msg) {
            this.msg = msg;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            Reply reply;
            if (response instanceof MbusResponse) {
                reply = ((MbusResponse)response).getReply();
            } else {
                reply = new EmptyReply();
                reply.swapState(msg);
            }
            Error err = StatusCodes.toMbusError(response.getStatus());
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
