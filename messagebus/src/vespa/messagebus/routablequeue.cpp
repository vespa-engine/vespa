// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routablequeue.h"

namespace mbus {

RoutableQueue::RoutableQueue()
    : _monitor("mbus::RoutableQueue::_monitor", true),
      _queue()
{ }

RoutableQueue::~RoutableQueue()
{
    while (_queue.size() > 0) {
        Routable *r = _queue.front();
        _queue.pop();
        delete r;
    }
}

uint32_t
RoutableQueue::size()
{
    vespalib::MonitorGuard guard(_monitor);
    return _queue.size();
}

void
RoutableQueue::enqueue(Routable::UP r)
{
    vespalib::MonitorGuard guard(_monitor);
    _queue.push(r.get());
    r.release();
    if (_queue.size() == 1) {
        guard.broadcast(); // support multiple readers
    }
}

Routable::UP
RoutableQueue::dequeue(uint32_t msTimeout)
{
    FastOS_Time t;
    t.SetNow();
    uint32_t msLeft = msTimeout;
    vespalib::MonitorGuard guard(_monitor);
    while (_queue.size() == 0 && msLeft > 0) {
        if (!guard.wait(msLeft) || _queue.size() > 0) {
            break;
        }
        uint32_t elapsed = (uint32_t)t.MilliSecsToNow();
        msLeft = (elapsed > msTimeout) ? 0 : msTimeout - elapsed;
    }
    if (_queue.size() == 0) {
        return Routable::UP();
    }
    Routable::UP ret(_queue.front());
    _queue.pop();
    return ret;
}

void
RoutableQueue::handleMessage(Message::UP msg)
{
    Routable::UP r(msg.release());
    enqueue(std::move(r));
}

void
RoutableQueue::handleReply(Reply::UP reply)
{
    Routable::UP r(reply.release());
    enqueue(std::move(r));
}

} // namespace mbus
