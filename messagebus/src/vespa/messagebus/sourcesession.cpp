// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "sourcesession.h"
#include "errorcode.h"
#include "messagebus.h"
#include "replygate.h"
#include "tracelevel.h"
#include <vespa/messagebus/routing/routingtable.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

using vespalib::make_string;
using vespalib::make_ref_counted;

namespace mbus {

SourceSession::SourceSession(MessageBus &mbus, const SourceSessionParams &params)
    : _lock(),
      _mbus(mbus),
      _gate(make_ref_counted<ReplyGate>(_mbus)),
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
    if (msg->getTimeRemaining() == 0ms) {
        msg->setTimeRemaining(_timeout);
    }
    uint32_t my_pending_count = 0;
    {
        std::lock_guard guard(_lock);
        if (_closed) {
            return Result(Error(ErrorCode::SEND_QUEUE_CLOSED, "Source session is closed."), std::move(msg));
        }
        my_pending_count = getPendingCount();
        if (_throttlePolicy && !_throttlePolicy->canSend(*msg, my_pending_count)) {
            return Result(Error(ErrorCode::SEND_QUEUE_FULL,
                                make_string("Too much pending data (%d messages).", my_pending_count)),
                          std::move(msg));
        }
        msg->pushHandler(_replyHandler);
        if (_throttlePolicy) {
            _throttlePolicy->processMessage(*msg);
        }
        ++my_pending_count;
        _pendingCount.store(my_pending_count, std::memory_order_relaxed);
    }
    if (msg->getTrace().shouldTrace(TraceLevel::COMPONENT)) {
        msg->getTrace().trace(TraceLevel::COMPONENT,
                              make_string("Source session accepted a %d byte message. %d message(s) now pending.",
                                          msg->getApproxSize(), my_pending_count));
    }
    msg->pushHandler(*this);
    _sequencer.handleMessage(std::move(msg));
    return Result();
}

void
SourceSession::handleReply(Reply::UP reply)
{
    bool done;
    uint32_t my_pending_count = 0;
    {
        std::lock_guard guard(_lock);
        my_pending_count = getPendingCount();
        assert(my_pending_count > 0);
        --my_pending_count;
        _pendingCount.store(my_pending_count, std::memory_order_relaxed);
        if (_throttlePolicy) {
            _throttlePolicy->processReply(*reply);
        }
        done = (_closed && my_pending_count == 0);
    }
    if (reply->getTrace().shouldTrace(TraceLevel::COMPONENT)) {
        reply->getTrace().trace(TraceLevel::COMPONENT,
                                make_string("Source session received reply. %d message(s) now pending.", my_pending_count));
    }
    IReplyHandler &handler = reply->getCallStack().pop(*reply);
    handler.handleReply(std::move(reply));
    if (done) {
        {
            std::lock_guard guard(_lock);
            assert(getPendingCount() == 0);
            assert(_closed);
            _done = true;
        }
        _cond.notify_all();
    }
}

void
SourceSession::close()
{
    std::unique_lock guard(_lock);
    _closed = true;
    if (getPendingCount() == 0) {
        _done = true;
    }
    while (!_done) {
        _cond.wait(guard);
    }
}

SourceSession &
SourceSession::setTimeout(duration timeout)
{
    std::lock_guard guard(_lock);
    _timeout = timeout;
    return *this;
}

} // namespace mbus
