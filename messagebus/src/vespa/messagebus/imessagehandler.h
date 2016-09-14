// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include "message.h"

namespace mbus {

/**
 * This interface is implemented by application components that want
 * to handle incoming messages received from either an
 * IntermediateSession or a DestinationSession.
 **/
class IMessageHandler
{
protected:
    IMessageHandler() = default;
public:
    IMessageHandler(const IMessageHandler &) = delete;
    IMessageHandler & operator = (const IMessageHandler &) = delete;
    virtual ~IMessageHandler() {}

    /**
     * This method is invoked by messagebus to deliver a Message.
     *
     * @param message the Message being delivered
     **/
    virtual void handleMessage(Message::UP message) = 0;
};

} // namespace mbus

