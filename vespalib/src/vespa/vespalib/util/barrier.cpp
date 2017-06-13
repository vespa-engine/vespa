// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "barrier.h"

namespace vespalib {

bool
Barrier::await()
{
    MonitorGuard guard(_monitor);
    if (_n == 0) {
        return false;
    }
    if (_count == _next) {
        _next += _n;
    }
    if (++_count == _next) {
        guard.broadcast();
    } else {
        size_t limit = _next;
        while ((_count - limit) > _n) {
            if (_n == 0) {
                return false;
            }
            guard.wait();
        }
    }
    return true;
}

void
Barrier::destroy()
{
    MonitorGuard guard(_monitor);
    _n = 0;
    guard.broadcast();
}

} // namespace vespalib
