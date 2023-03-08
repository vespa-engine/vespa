// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "intermediatesession.h"
#include "messagebus.h"
#include "replygate.h"

using vespalib::make_ref_counted;

namespace mbus {

IntermediateSession::IntermediateSession(MessageBus &mbus, const IntermediateSessionParams &params) :
    _mbus(mbus),
    _name(params.getName()),
    _msgHandler(params.getMessageHandler()),
    _replyHandler(params.getReplyHandler()),
    _gate(make_ref_counted<ReplyGate>(_mbus))
{ }

IntermediateSession::~IntermediateSession()
{
    _gate->close();
    close();
}

void
IntermediateSession::close()
{
    _mbus.unregisterSession(_name);
    _mbus.sync();
}

void
IntermediateSession::forward(Routable::UP routable)
{
    if (routable->isReply()) {
        forward(Reply::UP(static_cast<Reply*>(routable.release())));
    } else {
        forward(Message::UP(static_cast<Message*>(routable.release())));
    }
}

void
IntermediateSession::forward(Reply::UP reply)
{
    IReplyHandler &handler = reply->getCallStack().pop(*reply);
    handler.handleReply(std::move(reply));
}

void
IntermediateSession::forward(Message::UP msg)
{
    msg->pushHandler(*this);
    _gate->handleMessage(std::move(msg));
}

void
IntermediateSession::handleMessage(Message::UP msg)
{
    _msgHandler.handleMessage(std::move(msg));
}

void
IntermediateSession::handleReply(Reply::UP reply)
{
    _replyHandler.handleReply(std::move(reply));
}

const string
IntermediateSession::getConnectionSpec() const
{
    return _mbus.getConnectionSpec() + "/" + _name;
}

} // namespace mbus
