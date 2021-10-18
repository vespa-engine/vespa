// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "monitored_refcount.h"

namespace vespalib {

/*
 * Class containing a reference to a monitored reference count,
 * intended to block teardown of the class owning the monitored
 * reference count.
 */
class RetainGuard {
public:
    RetainGuard(MonitoredRefCount & refCount) noexcept
        : _refCount(&refCount)
    {
        _refCount->retain();
    }
    RetainGuard(const RetainGuard & rhs) = delete;
    RetainGuard & operator=(const RetainGuard & rhs) = delete;
    RetainGuard(RetainGuard && rhs) noexcept
        : _refCount(rhs._refCount)
    {
        rhs._refCount = nullptr;
    }
    RetainGuard & operator=(RetainGuard && rhs) noexcept {
        release();
        _refCount = rhs._refCount;
        rhs._refCount = nullptr;
        return *this;
    }
    ~RetainGuard() { release(); }
private:
    void release() noexcept{
        if (_refCount != nullptr) {
            _refCount->release();
            _refCount = nullptr;
        }
    }
    MonitoredRefCount * _refCount;
};

}
