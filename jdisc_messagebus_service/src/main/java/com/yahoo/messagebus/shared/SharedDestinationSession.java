// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public class SharedDestinationSession extends AbstractResource implements MessageHandler, ServerSession {

    private static final Logger log = Logger.getLogger(SharedDestinationSession.class.getName());
    private final AtomicReference<MessageHandler> msgHandler = new AtomicReference<>();
    private final SharedMessageBus mbus;
    private final DestinationSession session;
    private final ResourceReference mbusReference;

    public SharedDestinationSession(SharedMessageBus mbus, DestinationSessionParams params) {
        this.mbus = mbus;
        this.msgHandler.set(params.getMessageHandler());
        this.session = mbus.messageBus().createDestinationSession(params.setMessageHandler(this));
        this.mbusReference = mbus.refer();
    }

    public DestinationSession session() {
        return session;
    }

    @Override
    public void sendReply(Reply reply) {
        session.reply(reply);
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
    public String connectionSpec() {
        return session.getConnectionSpec();
    }

    @Override
    public String name() {
        return session.getName();
    }

    @Override
    protected void destroy() {
        log.log(LogLevel.DEBUG, "Destroying shared destination session.");
        session.destroy();
        mbusReference.close();
    }
}
