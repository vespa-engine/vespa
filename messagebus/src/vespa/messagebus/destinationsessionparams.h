// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "imessagehandler.h"
#include "common.h"

namespace mbus {

/**
 * To facilitate several configuration parameters to the {@link MessageBus#createDestinationSession(MessageHandler,
 * DestinationSessionParams)}, all parameters are held by this class. This class has reasonable default values for each
 * parameter.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class DestinationSessionParams {
private:
    string           _name;
    bool             _broadcastName;
    bool             _defer_registration;
    IMessageHandler *_handler;

public:
    /**
     * Constructs a new instance of this class with default values.
     */
    DestinationSessionParams();

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
    DestinationSessionParams &setName(const string &name) { _name = name; return *this; }

    /**
     * Returns whether or not to broadcast the name of this session on the network.
     *
     * @return True to broadcast, false otherwise.
     */
    bool getBroadcastName() const { return _broadcastName; }

    [[nodiscard]] bool defer_registration() const noexcept { return _defer_registration; }

    /**
     * Sets whether or not to broadcast the name of this session on the network.
     *
     * @param broadcastName True to broadcast, false otherwise.
     * @return This, to allow chaining.
     */
    DestinationSessionParams &setBroadcastName(bool broadcastName) { _broadcastName = broadcastName; return *this; }

    DestinationSessionParams& defer_registration(bool defer) noexcept {
        _defer_registration = defer;
        return *this;
    }

    /**
     * Returns the handler to receive incoming messages. If you call this method without first assigning a
     * message handler to this object, you wil de-ref null.
     *
     * @return The handler.
     */
    IMessageHandler &getMessageHandler() const { return *_handler; }

    /**
     * Sets the handler to receive incoming messages.
     *
     * @param handler The handler to set.
     * @return This, to allow chaining.
     */
    DestinationSessionParams &setMessageHandler(IMessageHandler &handler) { _handler = &handler; return *this; }
};

} // namespace mbus

