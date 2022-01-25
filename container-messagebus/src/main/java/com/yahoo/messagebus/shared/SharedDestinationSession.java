// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public class SharedDestinationSession extends AbstractResource implements MessageHandler, ServerSession {

    private static final Logger log = Logger.getLogger(SharedDestinationSession.class.getName());
    private final AtomicReference<MessageHandler> msgHandler = new AtomicReference<>();
    private final DestinationSession session;
    private final ResourceReference mbusReference;

    SharedDestinationSession(SharedMessageBus mbus, DestinationSessionParams params) {
        this.msgHandler.set(params.getMessageHandler());
        this.session = mbus.messageBus().createDetachedDestinationSession(params.setMessageHandler(this));
        this.mbusReference = mbus.refer(this);
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
    public void connect() {
        session.connect();
    }

    @Override public void disconnect() { session.disconnect(); }

    @Override
    protected void destroy() {
        log.log(Level.FINE, "Destroying shared destination session.");
        session.destroy();
        mbusReference.close();
    }
}
