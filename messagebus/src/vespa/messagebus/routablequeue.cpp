// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routablequeue.h"

using namespace std::chrono;

namespace mbus {

RoutableQueue::RoutableQueue()
    : _monitor(),
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
RoutableQueue::dequeue(duration msTimeout)
{
    steady_clock::time_point startTime = steady_clock::now();
    duration msLeft = msTimeout;
    vespalib::MonitorGuard guard(_monitor);
    while (_queue.size() == 0 && msLeft > duration::zero()) {
        if (!guard.wait(msLeft) || _queue.size() > 0) {
            break;
        }
        duration elapsed = (steady_clock::now() - startTime);
        msLeft = (elapsed > msTimeout) ? duration::zero() : msTimeout - elapsed;
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
