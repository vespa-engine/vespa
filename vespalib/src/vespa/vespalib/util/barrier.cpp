// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "barrier.h"

namespace vespalib {

Barrier::Barrier(size_t n)
  : _n(n),
    _lock(),
    _cond(),
    _count(0),
    _next(0)
{}
Barrier::~Barrier() = default;

bool
Barrier::await()
{
    std::unique_lock guard(_lock);
    if (_n == 0) {
        return false;
    }
    if (_count == _next) {
        _next += _n;
    }
    if (++_count == _next) {
        _cond.notify_all();
    } else {
        size_t limit = _next;
        while ((_count - limit) > _n) {
            if (_n == 0) {
                return false;
            }
            _cond.wait(guard);
        }
    }
    return true;
}

void
Barrier::destroy()
{
    std::lock_guard guard(_lock);
    _n = 0;
    _cond.notify_all();
}

} // namespace vespalib
