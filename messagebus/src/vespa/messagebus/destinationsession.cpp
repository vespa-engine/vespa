// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "destinationsession.h"
#include "messagebus.h"
#include "emptyreply.h"

namespace mbus {

DestinationSession::DestinationSession(MessageBus &mbus, const DestinationSessionParams &params) :
    _mbus(mbus),
    _name(params.getName()),
    _msgHandler(params.getMessageHandler())
{ }

DestinationSession::~DestinationSession() {
    close();
}

void
DestinationSession::close() {
    _mbus.unregisterSession(_name);
    _mbus.sync();
}

void
DestinationSession::acknowledge(Message::UP msg) {
    Reply::UP ack(new EmptyReply());
    ack->swapState(*msg);
    reply(std::move(ack));
}

void
DestinationSession::reply(Reply::UP ret) {
    IReplyHandler &handler = ret->getCallStack().pop(*ret);
    handler.handleReply(std::move(ret));
}

void
DestinationSession::handleMessage(Message::UP msg) {
    _msgHandler.handleMessage(std::move(msg));
}

const string
DestinationSession::getConnectionSpec() const {
    return _mbus.getConnectionSpec() + "/" + _name;
}

} // namespace mbus
