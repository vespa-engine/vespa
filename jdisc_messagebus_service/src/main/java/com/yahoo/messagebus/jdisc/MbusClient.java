// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.google.inject.Inject;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDeniedException;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.shared.ClientSession;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public final class MbusClient extends AbstractResource implements ClientProvider, ReplyHandler {

    private static final Logger log = Logger.getLogger(MbusClient.class.getName());
    private final BlockingQueue<MbusRequest> queue = new LinkedBlockingQueue<>();
    private final ClientSession session;
    private final Thread thread = new Thread(new SenderTask(), "MbusClient");
    private volatile boolean done = false;
    private final ResourceReference sessionReference;

    @Inject
    public MbusClient(final ClientSession session) {
        this.session = session;
        this.sessionReference = session.refer();
    }

    @Override
    public void start() {
        thread.start();
    }

    @Override
    public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
        if (!(request instanceof MbusRequest)) {
            throw new RequestDeniedException(request);
        }
        final Message msg = ((MbusRequest)request).getMessage();
        msg.getTrace().trace(6, "Request received by MbusClient.");
        msg.pushHandler(null); // save user context
        final Long timeout = request.timeRemaining(TimeUnit.MILLISECONDS);
        if (timeout != null) {
            msg.setTimeReceivedNow();
            msg.setTimeRemaining(Math.max(1, timeout)); // negative or zero timeout has semantics
        }
        msg.setContext(handler);
        msg.pushHandler(this);
        queue.add((MbusRequest)request);
        return null;
    }

    @Override
    public void handleTimeout(final Request request, final ResponseHandler handler) {
        // ignore, mbus has guaranteed reply
    }

    @Override
    protected void destroy() {
        log.log(LogLevel.DEBUG, "Destroying message bus client.");
        sessionReference.close();
        done = true;
    }

    @Override
    public void handleReply(final Reply reply) {
        reply.getTrace().trace(6, "Reply received by MbusClient.");
        final ResponseHandler handler = (ResponseHandler)reply.getContext();
        reply.popHandler(); // restore user context
        try {
            handler.handleResponse(new MbusResponse(StatusCodes.fromMbusReply(reply), reply))
                   .close(IgnoredCompletionHandler.INSTANCE);
        } catch (final Exception e) {
            log.log(LogLevel.WARNING, "Ignoring exception thrown by ResponseHandler.", e);
        }
    }

    private void sendBlocking(final MbusRequest request) {
        while (!sendMessage(request)) {
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                // ignore
            }
        }
    }

    private boolean sendMessage(final MbusRequest request) {
        Error error;
        final Long millis = request.timeRemaining(TimeUnit.MILLISECONDS);
        if (millis != null && millis <= 0) {
            error = new Error(ErrorCode.TIMEOUT, request.getTimeout(TimeUnit.MILLISECONDS) + " millis");
        } else if (request.isCancelled()) {
            error = new Error(ErrorCode.APP_FATAL_ERROR, "request cancelled");
        } else {
            try {
                error = session.sendMessage(request.getMessage()).getError();
            } catch (final Exception e) {
                error = new Error(ErrorCode.FATAL_ERROR, e.toString());
            }
        }
        if (error == null) {
            return true;
        }
        if (error.isFatal()) {
            final Reply reply = new EmptyReply();
            reply.swapState(request.getMessage());
            reply.addError(error);
            reply.popHandler().handleReply(reply);
            return true;
        }
        return false;
    }

    private class SenderTask implements Runnable {

        @Override
        public void run() {
            while (!done) {
                try {
                    final MbusRequest request = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (request == null) {
                        continue;
                    }
                    sendBlocking(request);
                } catch (final Exception e) {
                    log.log(LogLevel.WARNING, "Ignoring exception thrown by MbusClient.", e);
                }
            }
        }
    }
}
