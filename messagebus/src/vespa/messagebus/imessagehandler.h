// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace mbus {

class Message;

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
    virtual ~IMessageHandler() = default;

    /**
     * This method is invoked by messagebus to deliver a Message.
     *
     * @param message the Message being delivered
     **/
    virtual void handleMessage(std::unique_ptr<Message> message) = 0;
};

} // namespace mbus

