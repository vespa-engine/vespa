// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include <condition_variable>

namespace proton {

class RetainGuard;
/*
 * Class containing a reference count that can be waited on to become zero.
 * Typically ancestor or member of a class that has to be careful of when
 * portions object can be properly torn down before destruction itself.
 */
class MonitoredRefCount
{
    std::mutex              _lock;
    std::condition_variable _cv;
    uint32_t                _refCount;
    void retain() noexcept;
    void release() noexcept;
    friend RetainGuard;
public:
    MonitoredRefCount();
    virtual ~MonitoredRefCount();
    void waitForZeroRefCount();
};

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

} // namespace proton
