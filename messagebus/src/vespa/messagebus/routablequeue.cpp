// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routablequeue.h"

using namespace std::chrono;

namespace mbus {

RoutableQueue::RoutableQueue()
    : _lock(),
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
    std::lock_guard guard(_lock);
    return _queue.size();
}

void
RoutableQueue::enqueue(Routable::UP r)
{
    std::unique_lock guard(_lock);
    _queue.push(r.get());
    r.release();
    if (_queue.size() == 1) {
        guard.unlock();
        _cond.notify_all(); // support multiple readers
    }
}

Routable::UP
RoutableQueue::dequeue(duration timeout)
{
    steady_clock::time_point startTime = steady_clock::now();
    duration left = timeout;
    std::unique_lock guard(_lock);
    while (_queue.size() == 0 && left > duration::zero()) {
        if ((_cond.wait_for(guard, left) == std::cv_status::timeout) || (_queue.size() > 0)) {
            break;
        }
        duration elapsed = (steady_clock::now() - startTime);
        left = (elapsed > timeout) ? duration::zero() : timeout - elapsed;
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
    enqueue(std::move(msg));
}

void
RoutableQueue::handleReply(Reply::UP reply)
{
    enqueue(std::move(reply));
}

} // namespace mbus
