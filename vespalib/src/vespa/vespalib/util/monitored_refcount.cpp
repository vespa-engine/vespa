// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "monitored_refcount.h"
#include <cassert>

namespace vespalib {

MonitoredRefCount::MonitoredRefCount()
    : _lock(),
      _cv(),
      _refCount(0u)
{
}

MonitoredRefCount::~MonitoredRefCount()
{
    assert(_refCount == 0u);
}

void
MonitoredRefCount::retain() noexcept
{
    std::lock_guard<std::mutex> guard(_lock);
    ++_refCount;
}

void
MonitoredRefCount::release() noexcept
{
    std::lock_guard<std::mutex> guard(_lock);
    --_refCount;
    if (_refCount == 0u) {
        _cv.notify_all();
    }
}

void
MonitoredRefCount::waitForZeroRefCount()
{
    std::unique_lock<std::mutex> guard(_lock);
    _cv.wait(guard, [this] { return (_refCount == 0u); });
}

bool
MonitoredRefCount::has_zero_ref_count()
{
    std::unique_lock<std::mutex> guard(_lock);
    return (_refCount == 0u);
}

}
