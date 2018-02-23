// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.log.LogLevel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * A session supporting receiving and replying to messages. A destination is expected to reply to every message
 * received.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public final class DestinationSession implements MessageHandler {

    private static Logger log = Logger.getLogger(DestinationSession.class.getName());
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final String name;
    private final boolean broadcastName;
    private final MessageBus mbus;
    private final MessageHandler msgHandler;

    /**
     * This constructor is package private since only MessageBus is supposed to instantiate it.
     *
     * @param mbus   The message bus that created this instance.
     * @param params The parameter object for this session.
     */
    DestinationSession(MessageBus mbus, DestinationSessionParams params) {
        this.mbus = mbus;
        this.name = params.getName();
        this.broadcastName = params.getBroadcastName();
        this.msgHandler = params.getMessageHandler();
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
     * This method will deadlock if you call it from the message handler.
     */
    public void close() {
        mbus.unregisterSession(name, broadcastName);
        mbus.sync();
    }

    /**
     * Conveniece method for acknowledging a message back to the sender.
     *
     * This is equivalent to:
     * <pre>
     *     Reply ack = new EmptyReply();
     *     ack.swapState(msg);
     *     reply(ack);
     * </pre>
     *
     * Messages should be acknowledged when
     * <ul>
     *     <li>this destination has safely and permanently applied the message, or
     *     <li>an intermediate determines that the purpose of the message is fullfilled without forwarding the message
     * </ul>
     *
     * @param msg The message to acknowledge back to the sender.
     * @see #reply
     */
    public void acknowledge(Message msg) {
        Reply ack = new EmptyReply();
        msg.swapState(ack);
        reply(ack);
    }

    /**
     * Sends a reply to a message. The reply will propagate back to the original sender, prefering the same route as it
     * used to reach the detination.
     *
     * @param reply The reply, created from the message this is a reply to.
     */
    public void reply(Reply reply) {
        ReplyHandler handler = reply.popHandler();
        handler.handleReply(reply);
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

}
