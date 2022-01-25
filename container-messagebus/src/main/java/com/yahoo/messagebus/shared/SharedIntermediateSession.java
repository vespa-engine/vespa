// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.IntermediateSession;
import com.yahoo.messagebus.IntermediateSessionParams;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Result;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public class SharedIntermediateSession extends AbstractResource
        implements ClientSession, ServerSession, MessageHandler, ReplyHandler
{

    private static final Logger log = Logger.getLogger(SharedIntermediateSession.class.getName());
    private final AtomicReference<MessageHandler> msgHandler = new AtomicReference<>();
    private final IntermediateSession session;
    private final ResourceReference mbusReference;

    public SharedIntermediateSession(SharedMessageBus mbus, IntermediateSessionParams params) {
        if (params.getReplyHandler() != null) {
            throw new IllegalArgumentException("Reply handler must be null.");
        }
        this.msgHandler.set(params.getMessageHandler());
        this.session = mbus.messageBus().createDetachedIntermediateSession(params.setReplyHandler(this)
                                                                                 .setMessageHandler(this));
        this.mbusReference = mbus.refer(this);
    }

    public IntermediateSession session() {
        return session;
    }

    @Override
    public Result sendMessage(Message msg) {
        session.forward(msg);
        return Result.ACCEPTED;
    }

    @Override
    public void sendReply(Reply reply) {
        session.forward(reply);
    }

    @Override
    public MessageHandler getMessageHandler() {
        return msgHandler.get();
    }

    @Override
    public void setMessageHandler(MessageHandler msgHandler) {
        if (!this.msgHandler.compareAndSet(null, msgHandler)) {
            throw new IllegalStateException("Message handler already registered.");
        }
    }

    @Override
    public void handleMessage(Message msg) {
        MessageHandler msgHandler = this.msgHandler.get();
        if (msgHandler == null) {
            Reply reply = new EmptyReply();
            reply.swapState(msg);
            reply.addError(new Error(ErrorCode.SESSION_BUSY, "Session not fully configured yet."));
            sendReply(reply);
            return;
        }
        msgHandler.handleMessage(msg);
    }

    @Override
    public void handleReply(Reply reply) {
        reply.popHandler().handleReply(reply);
    }

    @Override
    public String connectionSpec() {
        return session.getConnectionSpec();
    }

    @Override
    public String name() {
        return session.getName();
    }

    @Override
    public void connect() {
        session.connect();
    }

    @Override
    public void disconnect() { session.disconnect(); }

    @Override
    protected void destroy() {
        log.log(Level.FINE, "Destroying shared intermediate session.");
        session.destroy();
        mbusReference.close();
    }
}
