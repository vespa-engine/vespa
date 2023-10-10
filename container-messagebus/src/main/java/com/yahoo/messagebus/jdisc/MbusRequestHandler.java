// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;

/**
 * @author Simon Thoresen Hult
 */
public abstract class MbusRequestHandler extends AbstractRequestHandler implements MessageHandler {

    @Override
    public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
        if (!(request instanceof MbusRequest)) {
            throw new UnsupportedOperationException("Expected MbusRequest, got " + request.getClass().getName() + ".");
        }
        final Message msg = ((MbusRequest)request).getMessage();
        msg.pushHandler(new RespondingReplyHandler(handler));
        handleMessage(msg);
        return null;
    }

    private static class RespondingReplyHandler implements ReplyHandler {

        private final ResponseHandler handler;

        RespondingReplyHandler(final ResponseHandler handler) {
            this.handler = handler;
        }

        @Override
        public void handleReply(final Reply reply) {
            final MbusResponse response = new MbusResponse(StatusCodes.fromMbusReply(reply), reply);
            handler.handleResponse(response).close(IgnoringCompletionHandler.INSTANCE);
        }
    }

    private static class IgnoringCompletionHandler implements CompletionHandler {

        public static final IgnoringCompletionHandler INSTANCE = new IgnoringCompletionHandler();

        @Override
        public void completed() {

        }

        @Override
        public void failed(final Throwable t) {

        }
    }
}
