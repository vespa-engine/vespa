// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".replygate");

#include "replygate.h"

namespace mbus {

ReplyGate::ReplyGate(IMessageHandler &sender) :
    vespalib::ReferenceCounter(),
    _sender(sender),
    _open(true)
{
    // empty
}

void
ReplyGate::handleMessage(Message::UP msg)
{
    addRef();
    msg->pushHandler(*this, *this);
    _sender.handleMessage(std::move(msg));
}

void
ReplyGate::close()
{
    _open = false;
}

void
ReplyGate::handleReply(Reply::UP reply)
{
    if (_open) {
        IReplyHandler &handler = reply->getCallStack().pop(*reply);
        handler.handleReply(std::move(reply));
    } else {
        reply->discard();
    }
    subRef();
}

void
ReplyGate::handleDiscard(Context ctx)
{
    (void)ctx;
    subRef();
}

} // namespace mbus
