// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/common.h>
#include <memory>

namespace mbus {

    class Reply;
    class IProtocol;
    class IReplyHandler;
    class Message;
/**
 * A network owner is the object that instantiates and uses a network. The API to send messages
 * across the network is part of the Network interface, whereas this interface exposes the required
 * functionality of a network owner to be able to decode and deliver incoming messages.
 */
class INetworkOwner {
public:
    /**
     * Required for inheritance.
     */
    virtual ~INetworkOwner() { }

    /**
     * All messages are sent across the network with its accompanying protocol name so that it can be decoded at the
     * receiving end. The network queries its owner through this function to resolve the protocol from its name.
     *
     * @param name The name of the protocol to return.
     * @return The named protocol.
     */
    virtual IProtocol * getProtocol(const string &name) = 0;

    /**
     * All messages that arrive in the network layer is passed to its owner through this function.
     *
     * @param message The message that just arrived from the network.
     * @param session The name of the session that is the recipient of the request.
     */
    virtual void deliverMessage(std::unique_ptr<Message> message, const string &session) = 0;

    /**
     * All replies that arrive in the network layer is passed through this to unentangle it from the network thread.
     *
     * @param reply   The reply that just arrived from the network.
     * @param handler The handler that is to receive the reply.
     */
    virtual void deliverReply(std::unique_ptr<Reply> reply, IReplyHandler &handler) = 0;
};

} // namespace mbus

