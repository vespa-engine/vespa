// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/routing/routingtable.h>
#include <vespa/messagebus/routing/routingnode.h>

namespace mbus {

class MessageBus;

/**
 * This class owns a message that is being sent by message bus. Once a reply is received, the message is
 * attached to it and returned to the application. After the reply has been propagated upwards, this object
 * deletes itself. This also implements the discard policy of {@link RoutingNode}.
 */
class SendProxy : public IDiscardHandler,
                  public IMessageHandler,
                  public IReplyHandler {
private:
    MessageBus     &_mbus;
    INetwork       &_net;
    Resender       *_resender;
    Message::UP     _msg;
    bool            _logTrace;
    RoutingNode::UP _root;

public:
    /**
     * Constructs a new instance of this class to maintain sending of a single message.
     *
     * @param mbus     The message bus that owns this.
     * @param net      The network layer to transmit through.
     * @param resender The resender to use.
     */
    SendProxy(MessageBus &mbus, INetwork &net, Resender *resender);

    void handleDiscard(Context ctx) override;
    void handleMessage(Message::UP msg) override;
    void handleReply(Reply::UP reply) override;
};

} // namespace mbus

