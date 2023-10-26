// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "destinationsession.h"
#include "messagebus.h"
#include "emptyreply.h"
#include <cassert>

namespace mbus {

DestinationSession::DestinationSession(MessageBus &mbus, const DestinationSessionParams &params) :
    _mbus(mbus),
    _name(params.getName()),
    _msgHandler(params.getMessageHandler()),
    _session_registered(!params.defer_registration()),
    _broadcast_name(params.getBroadcastName())
{ }

DestinationSession::~DestinationSession() {
    close();
}

void
DestinationSession::register_session_deferred() {
    assert(!_session_registered);
    _mbus.register_session(*this, _name, _broadcast_name);
    _session_registered = true;
}

void
DestinationSession::close() {
    if (_session_registered) {
        _mbus.unregisterSession(_name);
        _mbus.sync();
        _session_registered = false;
    }
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
