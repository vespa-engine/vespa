// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "replygate.h"
#include "message.h"
#include "reply.h"

namespace mbus {

ReplyGate::ReplyGate(IMessageHandler &sender) :
    _sender(sender),
    _open(true)
{ }

void
ReplyGate::handleMessage(Message::UP msg)
{
    internal_addref();
    msg->pushHandler(*this, *this);
    _sender.handleMessage(std::move(msg));
}

void
ReplyGate::close()
{
    _open.store(false, std::memory_order_relaxed);
}

void
ReplyGate::handleReply(Reply::UP reply)
{
    if (_open.load(std::memory_order_relaxed)) {
        IReplyHandler &handler = reply->getCallStack().pop(*reply);
        handler.handleReply(std::move(reply));
    } else {
        reply->discard();
    }
    internal_subref();
}

void
ReplyGate::handleDiscard(Context)
{
    internal_subref();
}

} // namespace mbus
