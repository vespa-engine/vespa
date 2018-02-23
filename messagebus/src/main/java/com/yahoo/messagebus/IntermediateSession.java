// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.log.LogLevel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * A session which supports receiving, forwarding and acknowledgement of messages. An intermediate session is expacted
 * to either forward or acknowledge every message received.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public final class IntermediateSession implements MessageHandler, ReplyHandler {

    private static final Logger log = Logger.getLogger(IntermediateSession.class.getName());
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final String name;
    private final boolean broadcastName;
    private final MessageHandler msgHandler;
    private final ReplyHandler replyHandler;
    private final MessageBus mbus;

    /**
     * This constructor is declared package private since only MessageBus is supposed to instantiate it.
     *
     * @param mbus   The message bus that created this instance.
     * @param params The parameter object for this session.
     */
    IntermediateSession(MessageBus mbus, IntermediateSessionParams params) {
        this.mbus = mbus;
        this.name = params.getName();
        this.broadcastName = params.getBroadcastName();
        this.msgHandler = params.getMessageHandler();
        this.replyHandler= params.getReplyHandler();
    }

    /**
     * Sets the destroyed flag to true. The very first time this method is called, it cleans up all its dependencies.
     * Even if you retain a reference to this object, all of its content is allowed to be garbage collected.
     *
     * @return True if content existed and was destroyed.
     */
    public boolean destroy() {
        if (!destroyed.getAndSet(true)) {
            close();
            return true;
        }
        return false;
    }

    /**
     * This method unregisters this session from message bus, effectively disabling any more messages from being
     * delivered to the message handler. After unregistering, this method calls {@link com.yahoo.messagebus.MessageBus#sync()}
     * as to ensure that there are no threads currently entangled in the handler.
     *
     * This method will deadlock if you call it from the message or reply handler.
     */
    public void close() {
        mbus.unregisterSession(name, broadcastName);
        mbus.sync();
    }

    /**
     * Forwards a routable to the next hop in its route. This method will never block.
     * @param routable the routable to forward.
     */
    public void forward(Routable routable) {
        if (routable instanceof Reply) {
            Reply reply = (Reply)routable;
            ReplyHandler handler = reply.popHandler();
            handler.handleReply(reply);
        } else {
            routable.pushHandler(this);
            mbus.handleMessage((Message)routable);
        }
    }

    /**
     * Returns the message handler of this session.
     *
     * @return The message handler.
     */
    public MessageHandler getMessageHandler() {
        return msgHandler;
    }

    /**
     * Returns the reply handler of this session.
     *
     * @return The reply handler.
     */
    public ReplyHandler getReplyHandler() {
        return replyHandler;
    }

    /**
     * Returns the connection spec string for this session. This returns a combination of the owning message bus' own
     * spec string and the name of this session.
     *
     * @return The connection string.
     */
    public String getConnectionSpec() {
        return mbus.getConnectionSpec() + "/" + name;
    }

    /**
     * Returns the name of this session.
     *
     * @return The session name.
     */
    public String getName() {
        return name;
    }

    @Override
    public void handleMessage(Message msg) {
        msgHandler.handleMessage(msg);
    }

    @Override
    public void handleReply(Reply reply) {
        if (destroyed.get()) {
            reply.discard();
        } else {
            replyHandler.handleReply(reply);
        }
    }
}
