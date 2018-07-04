// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * To facilitate several configuration parameters to the {@link MessageBus#createDestinationSession(DestinationSessionParams)},
 * all parameters are held by this class. This class has reasonable default values for each parameter.
 *
 * @author Simon Thoresen Hult
 */
public class DestinationSessionParams {

    // The session name to register with message bus.
    private String name = "destination";

    // Whether or not to broadcast name on network.
    private boolean broadcastName = true;

    // The handler to receive incoming messages.
    private MessageHandler handler = null;

    /**
     * Constructs a new instance of this class with default values.
     */
    public DestinationSessionParams() {
        // empty
    }

    /**
     * Implements the copy constructor.
     *
     * @param params The object to copy.
     */
    public DestinationSessionParams(DestinationSessionParams params) {
        name = params.name;
        broadcastName = params.broadcastName;
        handler = params.handler;
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
    public DestinationSessionParams setName(String name) {
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
     * Sets whether or not to broadcast the name of this session on the network.
     *
     * @param broadcastName True to broadcast, false otherwise.
     * @return This, to allow chaining.
     */
    public DestinationSessionParams setBroadcastName(boolean broadcastName) {
        this.broadcastName = broadcastName;
        return this;
    }

    /**
     * Returns the handler to receive incoming messages.
     *
     * @return The handler.
     */
    public MessageHandler getMessageHandler() {
        return handler;
    }

    /**
     * Sets the handler to recive incoming messages.
     *
     * @param handler The handler to set.
     * @return This, to allow chaining.
     */
    public DestinationSessionParams setMessageHandler(MessageHandler handler) {
        this.handler = handler;
        return this;
    }

}
