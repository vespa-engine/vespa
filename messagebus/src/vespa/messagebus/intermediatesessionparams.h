// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "imessagehandler.h"
#include "ireplyhandler.h"
#include "common.h"

namespace mbus {

/**
 * To facilitate several configuration parameters to the {@link MessageBus#createIntermediateSession(MessageHandler,
 * ReplyHandler, IntermediateSessionParams)}, all parameters are held by this class. This class has reasonable default
 * values for each parameter.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class IntermediateSessionParams {
private:
    string           _name;
    bool             _broadcastName;
    IMessageHandler *_msgHandler;
    IReplyHandler   *_replyHandler;

public:
    /**
     * Constructs a new instance of this class with default values.
     */
    IntermediateSessionParams();

    /**
     * Returns the name to register with message bus.
     *
     * @return The name.
     */
    const string &getName() const { return _name; }

    /**
     * Sets the name to register with message bus.
     *
     * @param name The name to set.
     * @return This, to allow chaining.
     */
    IntermediateSessionParams &setName(const string &name) { _name = name; return *this; }

    /**
     * Returns whether or not to broadcast the name of this session on the network.
     *
     * @return True to broadcast, false otherwise.
     */
    bool getBroadcastName() const { return _broadcastName; }

    /**
     * Sets whether or not to broadcast the name of this session on the network.
     *
     * @param broadcastName True to broadcast, false otherwise.
     * @return This, to allow chaining.
     */
    IntermediateSessionParams &setBroadcastName(bool broadcastName) { _broadcastName = broadcastName; return *this; }

    /**
     * Returns the handler to receive incoming replies. If you call this method without first assigning a
     * reply handler to this object, you wil de-ref null.
     *
     * @return The handler.
     */
    IReplyHandler &getReplyHandler() const { return *_replyHandler; }

    /**
     * Sets the handler to receive incoming replies.
     *
     * @param handler The handler to set.
     * @return This, to allow chaining.
     */
    IntermediateSessionParams &setReplyHandler(IReplyHandler &handler) { _replyHandler = &handler; return *this; }

    /**
     * Returns the handler to receive incoming messages. If you call this method without first assigning a
     * message handler to this object, you wil de-ref null.
     *
     * @return The handler.
     */
    IMessageHandler &getMessageHandler() const { return *_msgHandler; }

    /**
     * Sets the handler to receive incoming messages.
     *
     * @param handler The handler to set.
     * @return This, to allow chaining.
     */
    IntermediateSessionParams &setMessageHandler(IMessageHandler &handler) { _msgHandler = &handler; return *this; }
};

} // namespace mbus

