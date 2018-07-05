// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * To facilitate several configuration parameters to the {@link MessageBus#createIntermediateSession(IntermediateSessionParams)},
 * all parameters are held by this class. This class has reasonable default values for each parameter.
 *
 * @author Simon Thoresen Hult
 */
public class IntermediateSessionParams {

    // The session name to register with message bus.
    private String name = "intermediate";

    // Whether or not to broadcast name on network.
    private boolean broadcastName = true;

    // The handler to receive incoming replies.
    private ReplyHandler replyHandler = null;

    // The handler to receive incoming messages.
    private MessageHandler msgHandler = null;

    /**
     * Constructs a new instance of this class with default values.
     */
    public IntermediateSessionParams() {
        // empty
    }

    /**
     * Implements the copy constructor.
     *
     * @param params The object to copy.
     */
    public IntermediateSessionParams(IntermediateSessionParams params) {
        name = params.name;
        broadcastName = params.broadcastName;
        replyHandler = params.replyHandler;
        msgHandler = params.msgHandler;
    }

    /**
     * Returns the name to register with message bus.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name to register with message bus.
     *
     * @param name The name to set.
     * @return This, to allow chaining.
     */
    public IntermediateSessionParams setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Returns whether or not to broadcast the name of this session on the network.
     *
     * @return True to broadcast, false otherwise.
     */
    public boolean getBroadcastName() {
        return broadcastName;
    }

    /**
     * Returns the handler to receive incoming replies.
     *
     * @return The handler.
     */
    public ReplyHandler getReplyHandler() {
        return replyHandler;
    }

    /**
     * Sets the handler to recive incoming replies.
     *
     * @param handler The handler to set.
     * @return This, to allow chaining.
     */
    public IntermediateSessionParams setReplyHandler(ReplyHandler handler) {
        replyHandler = handler;
        return this;
    }

    /**
     * Returns the handler to receive incoming messages.
     *
     * @return The handler.
     */
    public MessageHandler getMessageHandler() {
        return msgHandler;
    }

    /**
     * Sets the handler to recive incoming messages.
     *
     * @param handler The handler to set.
     * @return This, to allow chaining.
     */
    public IntermediateSessionParams setMessageHandler(MessageHandler handler) {
        msgHandler = handler;
        return this;
    }

    /**
     * Sets whether or not to broadcast the name of this session on the network.
     *
     * @param broadcastName True to broadcast, false otherwise.
     * @return This, to allow chaining.
     */
    public IntermediateSessionParams setBroadcastName(boolean broadcastName) {
        this.broadcastName = broadcastName;
        return this;
    }
}
