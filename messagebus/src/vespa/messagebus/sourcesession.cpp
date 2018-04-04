// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "sourcesession.h"
#include "errorcode.h"
#include "messagebus.h"
#include "replygate.h"
#include "tracelevel.h"
#include <vespa/messagebus/routing/routingtable.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace mbus {

SourceSession::SourceSession(MessageBus &mbus, const SourceSessionParams &params)
    : _monitor(),
      _mbus(mbus),
      _gate(new ReplyGate(_mbus)),
      _sequencer(*_gate),
      _replyHandler(params.getReplyHandler()),
      _throttlePolicy(params.getThrottlePolicy()),
      _timeout(params.getTimeout()),
      _pendingCount(0),
      _closed(false),
      _done(false)
{
    assert(params.hasReplyHandler());
}

SourceSession::~SourceSession()
{
    // Ensure that no more replies propagate from mbus.
    _gate->close();
    _mbus.sync();

    // Tell gate that we will no longer use it.
    _gate->subRef();
}

Result
SourceSession::send(Message::UP msg, const string &routeName, bool parseIfNotFound)
{
    bool found = false;
    RoutingTable::SP rt = _mbus.getRoutingTable(msg->getProtocol());
    if (rt) {
        const Route *route = rt->getRoute(routeName);
        if (route != nullptr) {
            msg->setRoute(*route);
            found = true;
        } else if (!parseIfNotFound) {
            string str = make_string("Route '%s' not found.", routeName.c_str());
            return Result(Error(ErrorCode::ILLEGAL_ROUTE, str), std::move(msg));
        }
    } else if (!parseIfNotFound) {
        string str = make_string("No routing table available for protocol '%s'.", msg->getProtocol().c_str());
        return Result(Error(ErrorCode::ILLEGAL_ROUTE, str), std::move(msg));
    }
    if (!found) {
        msg->setRoute(Route::parse(routeName));
    }
    return send(std::move(msg));
}

Result
SourceSession::send(Message::UP msg, const Route &route)
{
    msg->setRoute(route);
    return send(std::move(msg));
}

Result
SourceSession::send(Message::UP msg)
{
    msg->setTimeReceivedNow();
    if (msg->getTimeRemaining() == 0) {
        msg->setTimeRemaining((uint64_t)(_timeout * 1000));
    }
    {
        vespalib::MonitorGuard guard(_monitor);
        if (_closed) {
            return Result(Error(ErrorCode::SEND_QUEUE_CLOSED, "Source session is closed."), std::move(msg));
        }
        if (_throttlePolicy.get() != nullptr && !_throttlePolicy->canSend(*msg, _pendingCount)) {
            return Result(Error(ErrorCode::SEND_QUEUE_FULL,
                                make_string("Too much pending data (%d messages).", _pendingCount)),
                          std::move(msg));
        }
        msg->pushHandler(_replyHandler);
        if (_throttlePolicy.get() != nullptr) {
            _throttlePolicy->processMessage(*msg);
        }
        ++_pendingCount;
    }
    if (msg->getTrace().shouldTrace(TraceLevel::COMPONENT)) {
        msg->getTrace().trace(TraceLevel::COMPONENT,
                              make_string("Source session accepted a %d byte message. %d message(s) now pending.",
                                          msg->getApproxSize(), _pendingCount));
    }
    msg->pushHandler(*this);
    _sequencer.handleMessage(std::move(msg));
    return Result();
}

void
SourceSession::handleReply(Reply::UP reply)
{
    bool done;
    {
        vespalib::MonitorGuard guard(_monitor);
        assert(_pendingCount > 0);
        --_pendingCount;
        if (_throttlePolicy.get() != nullptr) {
            _throttlePolicy->processReply(*reply);
        }
        done = (_closed && _pendingCount == 0);
    }
    if (reply->getTrace().shouldTrace(TraceLevel::COMPONENT)) {
        reply->getTrace().trace(TraceLevel::COMPONENT,
                                make_string("Source session received reply. %d message(s) now pending.", _pendingCount));
    }
    IReplyHandler &handler = reply->getCallStack().pop(*reply);
    handler.handleReply(std::move(reply));
    if (done) {
        vespalib::MonitorGuard guard(_monitor);
        assert(_pendingCount == 0);
        assert(_closed);
        _done = true;
        guard.broadcast();
    }
}

void
SourceSession::close()
{
    vespalib::MonitorGuard guard(_monitor);
    _closed = true;
    if (_pendingCount == 0) {
        _done = true;
    }
    while (!_done) {
        guard.wait();
    }
}

SourceSession &
SourceSession::setTimeout(double timeout)
{
    vespalib::MonitorGuard guard(_monitor);
    _timeout = timeout;
    return *this;
}

} // namespace mbus
